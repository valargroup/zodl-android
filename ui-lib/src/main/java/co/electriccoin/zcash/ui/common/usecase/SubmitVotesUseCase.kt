package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.model.voting.toDelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.toEncryptedSharesJson
import co.electriccoin.zcash.ui.common.model.voting.toSharePayloads
import co.electriccoin.zcash.ui.common.model.voting.toVoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.min
import kotlin.random.Random

class SubmitVotesUseCase(
    private val votingConfigRepository: VotingConfigRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingApiProvider: VotingApiProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getWalletSeedBytes: GetWalletSeedBytesUseCase,
    private val prepareVotingRound: PrepareVotingRoundUseCase,
) {
    suspend operator fun invoke(
        roundId: String,
        choices: Map<Int, Int>,
        onProgress: (VotingSubmissionProgress) -> Unit = {}
    ): VotingSubmissionResult =
        withContext(Dispatchers.IO) {
            if (choices.isEmpty()) {
                return@withContext VotingSubmissionResult(submittedProposalCount = 0)
            }

            val selectedAccount = requireNotNull(getSelectedWalletAccount()) {
                "No selected wallet account is available"
            }
            require(selectedAccount !is KeystoneAccount) {
                "Keystone voting is not implemented on Android yet"
            }

            when (val preparation = prepareVotingRound(roundId)) {
                is VotingRoundPreparationResult.Ready -> Unit
                is VotingRoundPreparationResult.Ineligible ->
                    error("Wallet is not eligible for this vote")
                is VotingRoundPreparationResult.WalletSyncing ->
                    error(
                        "Wallet sync is below the voting snapshot height " +
                            "(${preparation.scannedHeight ?: 0}/${preparation.snapshotHeight})"
                    )
            }

            val currentConfig = requireNotNull(votingConfigRepository.currentConfig.value) {
                "No active voting session is loaded"
            }
            val session = currentConfig.session
            val sessionRoundId = session.voteRoundId.toHex()
            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val serviceConfig = votingApiProvider.fetchServiceConfig()
            val voteServerUrl = serviceConfig.voteServers
                .firstOrNull()
                ?.url
                ?.trimEnd('/')
                ?: error("Voting server URL is not configured")
            val pirServerUrl = serviceConfig.pirServers
                .firstOrNull()
                ?.url
                ?.trimEnd('/')
                ?: error("PIR server URL is not configured")

            val recovery = requireNotNull(votingRecoveryRepository.get(roundId)) {
                "Voting round $roundId has not been prepared"
            }
            val bundleCount = recovery.bundleCount ?: error("Voting round $roundId has no prepared bundle count")
            val hotkeySeed = recovery.decodeHotkeySeed() ?: error("Voting round $roundId has no stored hotkey seed")

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizer.getWalletDbPath()
            val votingDbPath = File(walletDbPath)
                .parentFile
                ?.resolve("voting.sqlite3")
                ?.absolutePath
                ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val senderSeed = getWalletSeedBytes()
            val accountIndex = selectedAccount.hdAccountIndex.index.toInt()
            val allNotesJson = votingCryptoClient.getWalletNotesJson(
                walletDbPath = walletDbPath,
                snapshotHeight = session.snapshotHeight,
                networkId = networkId,
                accountUuidBytes = selectedAccount.sdkAccount.accountUuid.value
            )

            val singleShare = session.isLastMoment()
            val submitAtDeadline = session.shareSubmissionDeadline(singleShare)
            val sortedChoices = choices.toSortedMap()
            val totalChoices = sortedChoices.size

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, selectedAccount.sdkAccount.accountUuid.toString())
                val delegatedShareIndicesByTarget = votingCryptoClient.getShareDelegations(
                    dbHandle = dbHandle,
                    roundId = roundId
                ).groupBy { record ->
                    ShareDelegationTarget(
                        bundleIndex = record.bundleIndex,
                        proposalId = record.proposalId
                    )
                }.mapValuesTo(mutableMapOf()) { (_, records) ->
                    records.mapTo(mutableSetOf()) { it.shareIndex }
                }

                if (recovery.phase != VotingRecoveryPhase.DELEGATION_SUBMITTED &&
                    recovery.phase != VotingRecoveryPhase.VOTES_SUBMITTED &&
                    recovery.phase != VotingRecoveryPhase.SHARES_SUBMITTED
                ) {
                    repeat(bundleCount) { bundleIndex ->
                        onProgress(
                            VotingSubmissionProgress.Authorizing(
                                progress = (bundleIndex + 1).toFloat() / bundleCount.coerceAtLeast(1)
                            )
                        )

                        val cachedDelegationTxHash = votingCryptoClient.getDelegationTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex
                        )
                        if (cachedDelegationTxHash != null) {
                            val confirmation = awaitTxConfirmation(cachedDelegationTxHash)
                            require(confirmation.code == 0) {
                                confirmation.log.ifEmpty { "Delegation transaction failed" }
                            }

                            val vanPosition = confirmation.event("delegate_vote")
                                ?.attribute("leaf_index")
                                ?.toIntOrNull()
                                ?: error("Missing delegate_vote leaf_index for bundle $bundleIndex")
                            votingCryptoClient.storeVanPosition(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                position = vanPosition
                            )
                            return@repeat
                        }

                        val witnessesJson = votingCryptoClient.generateNoteWitnessesJson(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            walletDbPath = walletDbPath,
                            notesJson = allNotesJson
                        )
                        votingCryptoClient.storeWitnesses(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            witnessesJson = witnessesJson
                        )

                        val bundleNotesJson = allNotesJson.selectBundleNotesJson(witnessesJson)
                        votingCryptoClient.buildAndProveDelegation(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            pirServerUrl = pirServerUrl,
                            networkId = networkId,
                            notesJson = bundleNotesJson,
                            hotkeyRawSeed = hotkeySeed
                        )
                        votingRecoveryRepository.setPhase(roundId, VotingRecoveryPhase.DELEGATION_PROVED)

                        val submission = votingCryptoClient.getDelegationSubmission(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            senderSeed = senderSeed,
                            networkId = networkId,
                            accountIndex = accountIndex
                        )
                        val txResult = votingApiProvider.submitDelegation(submission.toDelegationRegistration())
                        require(txResult.code == 0) {
                            txResult.log.ifEmpty { "Delegation transaction was rejected" }
                        }
                        votingCryptoClient.storeDelegationTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            txHash = txResult.txHash
                        )

                        val confirmation = awaitTxConfirmation(txResult.txHash)
                        require(confirmation.code == 0) {
                            confirmation.log.ifEmpty { "Delegation transaction failed" }
                        }

                        val vanPosition = confirmation.event("delegate_vote")
                            ?.attribute("leaf_index")
                            ?.toIntOrNull()
                            ?: error("Missing delegate_vote leaf_index for bundle $bundleIndex")
                        votingCryptoClient.storeVanPosition(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            position = vanPosition
                        )
                    }

                    votingRecoveryRepository.setPhase(roundId, VotingRecoveryPhase.DELEGATION_SUBMITTED)
                }

                sortedChoices.entries.forEachIndexed { proposalIndex, (proposalId, choiceId) ->
                    val proposal = session.proposals.firstOrNull { it.id == proposalId }
                        ?: error("Unknown proposal id $proposalId for round $roundId")
                    val progressBase = proposalIndex + 1

                    if (proposalId in recovery.submittedProposalIds) {
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress = progressBase.toFloat() / totalChoices.coerceAtLeast(1)
                            )
                        )
                        return@forEachIndexed
                    }

                    if (proposal.options.none { option -> option.id == choiceId }) {
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress = progressBase.toFloat() / totalChoices.coerceAtLeast(1)
                            )
                        )
                        votingRecoveryRepository.markProposalSubmitted(roundId, proposalId)
                        return@forEachIndexed
                    }

                    repeat(bundleCount) { bundleIndex ->
                        val completedBundles = proposalIndex * bundleCount + bundleIndex + 1
                        val bundleTotal = totalChoices * bundleCount.coerceAtLeast(1)
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress = completedBundles.toFloat() / bundleTotal
                            )
                        )

                        val cachedVoteTxHash = votingCryptoClient.getVoteTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        )

                        if (cachedVoteTxHash != null) {
                            val confirmation = awaitTxConfirmation(cachedVoteTxHash)
                            require(confirmation.code == 0) {
                                confirmation.log.ifEmpty { "Vote commitment transaction failed" }
                            }

                            val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
                            votingCryptoClient.storeVanPosition(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                position = confirmedVanPosition
                            )

                            val storedCommitment = requireNotNull(
                                votingCryptoClient.getCommitmentBundle(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    proposalId = proposalId
                                )
                            ) {
                                "Missing stored vote commitment bundle for round $roundId bundle $bundleIndex proposal $proposalId"
                            }
                            submitMissingShares(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId,
                                choiceId = choiceId,
                                numOptions = proposal.options.size,
                                singleShare = singleShare,
                                submitAtDeadline = submitAtDeadline,
                                commitmentJson = storedCommitment.bundleJson,
                                commitmentBundle = storedCommitment.bundle,
                                vcTreePosition = vcTreePosition,
                                delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
                            )
                            return@repeat
                        }

                        val syncedHeight = votingCryptoClient.syncVoteTree(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            nodeUrl = voteServerUrl
                        )
                        check(syncedHeight >= 0) {
                            "Failed to synchronize vote tree for round $roundId"
                        }

                        val vanWitnessJson = votingCryptoClient.generateVanWitnessJson(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            anchorHeight = syncedHeight.toInt()
                        )
                        val vanWitness = vanWitnessJson.toVanWitnessSummary()
                        val commitment = votingCryptoClient.buildVoteCommitment(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            hotkeySeed = hotkeySeed,
                            proposalId = proposalId,
                            choice = choiceId,
                            numOptions = proposal.options.size,
                            witnessJson = vanWitnessJson,
                            vanPosition = vanWitness.position,
                            anchorHeight = vanWitness.anchorHeight,
                            networkId = networkId,
                            singleShare = singleShare
                        )
                        votingCryptoClient.storeCommitmentBundle(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            bundleJson = commitment.rawBundleJson,
                            vcTreePosition = 0L
                        )
                        val signature = CastVoteSignature(
                            voteAuthSig = votingCryptoClient.signCastVote(
                                hotkeySeed = hotkeySeed,
                                networkId = networkId,
                                roundId = roundId,
                                rVpk = commitment.rVpk,
                                vanNullifier = commitment.vanNullifier,
                                vanNew = commitment.voteAuthorityNoteNew,
                                voteCommitment = commitment.voteCommitment,
                                proposalId = proposalId,
                                anchorHeight = commitment.anchorHeight,
                                alphaV = commitment.alphaV
                            )
                        )
                        val txResult = votingApiProvider.submitVoteCommitment(
                            bundle = commitment.toVoteCommitmentBundle(),
                            signature = signature
                        )
                        require(txResult.code == 0) {
                            txResult.log.ifEmpty { "Vote commitment transaction was rejected" }
                        }
                        votingCryptoClient.storeVoteTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            txHash = txResult.txHash
                        )

                        val confirmation = awaitTxConfirmation(txResult.txHash)
                        require(confirmation.code == 0) {
                            confirmation.log.ifEmpty { "Vote commitment transaction failed" }
                        }

                        val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
                        votingCryptoClient.storeVanPosition(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            position = confirmedVanPosition
                        )
                        votingCryptoClient.storeCommitmentBundle(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            bundleJson = commitment.rawBundleJson,
                            vcTreePosition = vcTreePosition
                        )
                        submitMissingShares(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            choiceId = choiceId,
                            numOptions = proposal.options.size,
                            singleShare = singleShare,
                            submitAtDeadline = submitAtDeadline,
                            commitmentJson = commitment.rawBundleJson,
                            commitmentBundle = commitment.toVoteCommitmentBundle(),
                            vcTreePosition = vcTreePosition,
                            delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
                        )
                    }

                    votingRecoveryRepository.markProposalSubmitted(roundId, proposalId)
                }

                votingRecoveryRepository.setPhase(roundId, VotingRecoveryPhase.VOTES_SUBMITTED)
                votingRecoveryRepository.setPhase(roundId, VotingRecoveryPhase.SHARES_SUBMITTED)
                votingSessionStore.markRoundSubmitted(roundId, totalChoices)
                votingSessionStore.clearDraftVotes()

                VotingSubmissionResult(submittedProposalCount = totalChoices)
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
        }

    private suspend fun awaitTxConfirmation(txHash: String): TxConfirmation {
        repeat(TX_CONFIRMATION_RETRIES) {
            votingApiProvider.fetchTxConfirmation(txHash)?.let { confirmation ->
                return confirmation
            }
            delay(TX_CONFIRMATION_POLL_MS)
        }

        error("Transaction $txHash was not confirmed in time")
    }

    private suspend fun submitMissingShares(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        choiceId: Int,
        numOptions: Int,
        singleShare: Boolean,
        submitAtDeadline: Long?,
        commitmentJson: String,
        commitmentBundle: VoteCommitmentBundle,
        vcTreePosition: Long,
        delegatedShareIndicesByTarget: MutableMap<ShareDelegationTarget, MutableSet<Int>>
    ) {
        val target = ShareDelegationTarget(bundleIndex = bundleIndex, proposalId = proposalId)
        val existingShareIndices = delegatedShareIndicesByTarget.getOrPut(target) { mutableSetOf() }
        val payloads = votingCryptoClient.buildSharePayloadsJson(
            encSharesJson = commitmentBundle.encShares.toEncryptedSharesJson(),
            commitmentJson = commitmentJson,
            voteDecision = choiceId,
            numOptions = numOptions,
            vcTreePosition = vcTreePosition,
            singleShareMode = singleShare
        ).toSharePayloads().map { payload ->
            payload.withSubmitAt(randomSubmitAt(submitAtDeadline))
        }
        val pendingPayloads = payloads.filterNot { payload ->
            payload.encShare.shareIndex in existingShareIndices
        }

        if (pendingPayloads.isEmpty()) {
            return
        }

        val delegationResults = votingApiProvider.delegateShares(pendingPayloads, roundId)
        delegationResults.forEach { info ->
            val payload = pendingPayloads.firstOrNull { candidate ->
                candidate.encShare.shareIndex == info.shareIndex &&
                    candidate.proposalId == info.proposalId
            } ?: return@forEach
            val shareBlind = commitmentBundle.shareBlindFactors.getOrNull(info.shareIndex)
                ?: error(
                    "Missing share blind for proposal $proposalId share ${info.shareIndex}"
                )
            val nullifier = votingCryptoClient.computeShareNullifier(
                voteCommitment = commitmentBundle.voteCommitment,
                shareIndex = info.shareIndex,
                blind = shareBlind
            )
            votingCryptoClient.recordShareDelegation(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = info.proposalId,
                shareIndex = info.shareIndex,
                sentToUrls = info.acceptedByServers,
                nullifier = nullifier,
                submitAt = payload.submitAt
            )
            existingShareIndices += info.shareIndex
        }
    }

    private fun randomSubmitAt(deadlineEpochSeconds: Long?): Long {
        if (deadlineEpochSeconds == null) {
            return 0
        }

        val nowEpochSeconds = System.currentTimeMillis() / 1_000
        if (deadlineEpochSeconds <= nowEpochSeconds) {
            return 0
        }

        val window = deadlineEpochSeconds - nowEpochSeconds
        return nowEpochSeconds + Random.nextLong(until = window)
    }

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 0 else 1

    private fun co.electriccoin.zcash.ui.common.model.voting.VotingSession.isLastMoment(): Boolean {
        val durationSeconds = voteEndTime.epochSecond - ceremonyStart.epochSecond
        if (durationSeconds <= 0) {
            return false
        }

        val bufferSeconds = min(durationSeconds / 10, LAST_MOMENT_BUFFER_MAX_SECONDS)
        return (System.currentTimeMillis() / 1_000) >= voteEndTime.epochSecond - bufferSeconds
    }

    private fun co.electriccoin.zcash.ui.common.model.voting.VotingSession.shareSubmissionDeadline(
        singleShare: Boolean
    ): Long? {
        if (singleShare) {
            return null
        }

        val durationSeconds = voteEndTime.epochSecond - ceremonyStart.epochSecond
        if (durationSeconds <= 0) {
            return null
        }

        val bufferSeconds = min(durationSeconds / 10, LAST_MOMENT_BUFFER_MAX_SECONDS)
        return voteEndTime.epochSecond - bufferSeconds
    }

    private fun String.selectBundleNotesJson(witnessesJson: String): String {
        val noteObjectsByCommitment = mutableMapOf<String, JSONObject>()
        val notes = JSONArray(this)
        for (index in 0 until notes.length()) {
            val note = notes.getJSONObject(index)
            noteObjectsByCommitment[note.getString("commitment")] = note
        }

        val witnesses = JSONArray(witnessesJson)
        return JSONArray(
            buildList {
                for (index in 0 until witnesses.length()) {
                    val witness = witnesses.getJSONObject(index)
                    val commitment = witness.getString("note_commitment")
                    add(
                        noteObjectsByCommitment[commitment]
                            ?: error("Missing note for witness commitment $commitment")
                    )
                }
            }
        ).toString()
    }

    private fun TxConfirmation.castVoteLeafPositions(): Pair<Int, Long> {
        val rawLeafIndex = event("cast_vote")
            ?.attribute("leaf_index")
            ?: error("Missing cast_vote leaf_index")
        val leafParts = rawLeafIndex.split(',')
        require(leafParts.size == 2) {
            "Malformed cast_vote leaf_index: $rawLeafIndex"
        }

        val vanPosition = leafParts[0].trim().toIntOrNull()
            ?: error("Malformed VAN leaf position: ${leafParts[0]}")
        val voteCommitmentPosition = leafParts[1].trim().toLongOrNull()
            ?: error("Malformed vote commitment leaf position: ${leafParts[1]}")

        return vanPosition to voteCommitmentPosition
    }

    private fun String.toVanWitnessSummary(): VanWitnessSummary {
        val json = JSONObject(this)
        return VanWitnessSummary(
            position = json.getInt("position"),
            anchorHeight = json.getInt("anchor_height")
        )
    }

    private data class VanWitnessSummary(
        val position: Int,
        val anchorHeight: Int
    )

    private data class ShareDelegationTarget(
        val bundleIndex: Int,
        val proposalId: Int
    )

    private companion object {
        const val LAST_MOMENT_BUFFER_MAX_SECONDS = 3_600L
        const val TX_CONFIRMATION_RETRIES = 45
        const val TX_CONFIRMATION_POLL_MS = 2_000L
    }
}
