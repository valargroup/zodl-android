package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingEligibility
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

class PrepareVotingRoundUseCase(
    private val votingConfigRepository: VotingConfigRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingCryptoClient: VotingCryptoClient,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase
) {
    private val secureRandom = SecureRandom()

    suspend operator fun invoke(roundId: String): VotingRoundPreparationResult =
        withContext(Dispatchers.IO) {
            val config = requireNotNull(
                votingConfigRepository.currentConfig.value ?: votingConfigRepository.get()
            ) {
                "No active voting session is loaded"
            }
            val session = config.session
            val sessionRoundId = session.voteRoundId.toHex()

            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val synchronizer = synchronizerProvider.getSynchronizer()
            val scannedHeight = awaitFullyScannedHeight(synchronizer)
            if (scannedHeight == null || scannedHeight < session.snapshotHeight) {
                votingSessionStore.setEligibility(VotingEligibility.WALLET_SYNCING)
                return@withContext VotingRoundPreparationResult.WalletSyncing(
                    scannedHeight = scannedHeight,
                    snapshotHeight = session.snapshotHeight
                )
            }

            val selectedAccount = getSelectedWalletAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid
            val walletDbPath = synchronizer.getWalletDbPath()
            val votingDbPath = File(walletDbPath)
                .parentFile
                ?.resolve("voting.sqlite3")
                ?.absolutePath
                ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val recoverySnapshot = votingRecoveryRepository.get(roundId)

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, accountUuid.toString())
                val existingRoundState = votingCryptoClient.getRoundState(dbHandle, roundId)
                val (preparedBundleCount, eligibleWeight) = if (existingRoundState == null) {
                    votingCryptoClient.initializeRound(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        snapshotHeight = session.snapshotHeight,
                        eaPK = session.eaPK,
                        ncRoot = session.ncRoot,
                        nullifierIMTRoot = session.nullifierIMTRoot,
                        sessionJson = null
                    )

                    val notesJson = votingCryptoClient.getWalletNotesJson(
                        walletDbPath = walletDbPath,
                        snapshotHeight = session.snapshotHeight,
                        networkId = networkId,
                        accountUuidBytes = accountUuid.value
                    )
                    votingCryptoClient.setupBundles(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        notesJson = notesJson
                    ).also { setup ->
                        votingRecoveryRepository.storeBundleSetup(
                            roundId = roundId,
                            bundleCount = setup.bundleCount,
                            eligibleWeight = setup.eligibleWeight
                        )
                    }.let { setup -> setup.bundleCount to setup.eligibleWeight }
                } else {
                    (
                        recoverySnapshot?.bundleCount
                            ?: error("Voting round $roundId is missing the stored bundle count")
                        ) to (
                        recoverySnapshot.eligibleWeight
                            ?: error("Voting round $roundId is missing the stored eligible weight")
                        )
                }

                if (eligibleWeight <= 0L) {
                    votingSessionStore.setEligibility(VotingEligibility.INELIGIBLE)
                    return@withContext VotingRoundPreparationResult.Ineligible(
                        eligibleWeight = eligibleWeight,
                        bundleCount = preparedBundleCount
                    )
                }

                if (existingRoundState == null) {
                    val notesJson = votingCryptoClient.getWalletNotesJson(
                        walletDbPath = walletDbPath,
                        snapshotHeight = session.snapshotHeight,
                        networkId = networkId,
                        accountUuidBytes = accountUuid.value
                    )
                    val treeStateBytes = synchronizer.getTreeState(BlockHeight.new(session.snapshotHeight))
                    votingCryptoClient.storeTreeState(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        treeStateBytes = treeStateBytes
                    )
                    repeat(preparedBundleCount) { bundleIndex ->
                        val witnessesJson = votingCryptoClient.generateNoteWitnessesJson(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            walletDbPath = walletDbPath,
                            notesJson = notesJson
                        )
                        votingCryptoClient.storeWitnesses(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            witnessesJson = witnessesJson
                        )
                    }
                }

                val storedHotkeySeed = recoverySnapshot?.decodeHotkeySeed()
                val hotkeySeed = when {
                    storedHotkeySeed != null -> storedHotkeySeed
                    existingRoundState?.phase != null && existingRoundState.phase != RoundPhase.INITIALIZED -> {
                        error("Missing stored hotkey seed for resumed round $roundId")
                    }

                    else -> ByteArray(HOTKEY_SEED_BYTES).also(secureRandom::nextBytes)
                }
                val hotkey = votingCryptoClient.generateHotkey(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    seed = hotkeySeed
                )
                votingRecoveryRepository.storeHotkey(
                    roundId = roundId,
                    hotkeySeed = hotkeySeed,
                    hotkeyAddress = hotkey.address
                )
                votingSessionStore.setEligibility(VotingEligibility.ELIGIBLE)

                VotingRoundPreparationResult.Ready(
                    roundId = roundId,
                    bundleCount = preparedBundleCount,
                    eligibleWeight = eligibleWeight,
                    hotkeyAddress = hotkey.address
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
        }

    private suspend fun awaitFullyScannedHeight(synchronizer: Synchronizer): Long? {
        var scannedHeight = synchronizer.fullyScannedHeight.value?.value
        repeat(FULLY_SCANNED_HEIGHT_RETRIES) {
            if (scannedHeight != null && scannedHeight > 0) {
                return scannedHeight
            }

            delay(FULLY_SCANNED_HEIGHT_POLL_MS)
            scannedHeight = synchronizer.fullyScannedHeight.value?.value
        }

        return scannedHeight
    }

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 0 else 1

    private companion object {
        const val FULLY_SCANNED_HEIGHT_RETRIES = 5
        const val FULLY_SCANNED_HEIGHT_POLL_MS = 1_000L
        const val HOTKEY_SEED_BYTES = 64
    }
}
