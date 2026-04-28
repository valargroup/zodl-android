package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitVotesUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

class VoteConfirmSubmissionVM(
    private val args: VoteConfirmSubmissionArgs,
    votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    prepareVotingRound: PrepareVotingRoundUseCase,
    private val submitVotes: SubmitVotesUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val statusFlow = MutableStateFlow<VoteSubmissionStatus>(VoteSubmissionStatus.Idle)
    private val draftChoices = runCatching { args.choicesJson.toDraftChoices() }
        .getOrElse { throwable ->
            Log.e("VoteConfirmSubmission", "Failed to parse draft vote choices", throwable)
            emptyMap()
        }

    init {
        viewModelScope.launch {
            runCatching {
                prepareVotingRound(args.roundIdHex)
            }.onFailure { throwable ->
                Log.e(
                    "VoteConfirmSubmission",
                    "Failed to prepare voting round ${args.roundIdHex}",
                    throwable
                )
            }
        }
        if (draftChoices.isNotEmpty()) {
            viewModelScope.launch {
                runCatching {
                    val round = votingApiRepository.snapshot
                        .map { snapshot -> snapshot.rounds.firstOrNull { it.id == args.roundIdHex } }
                        .filterNotNull()
                        .first()
                    persistDraftChoices(round)
                }.onFailure { throwable ->
                    Log.e(
                        "VoteConfirmSubmission",
                        "Failed to persist draft vote choices for ${args.roundIdHex}",
                        throwable
                    )
                }
            }
        }
    }

    val state: StateFlow<LceState<VoteConfirmSubmissionState>> =
        combine(
            votingApiRepository.snapshot,
            votingRecoveryRepository.observe(args.roundIdHex),
            getSelectedWalletAccount.observe().map { account -> account is KeystoneAccount },
            statusFlow,
        ) { apiSnapshot, recovery, isKeystone, status ->
            apiSnapshot.rounds
                .firstOrNull { round -> round.id == args.roundIdHex }
                ?.let { round ->
                    createState(
                        round = round,
                        recovery = recovery,
                        isKeystone = isKeystone,
                        status = status
                    )
                }
        }.map { content ->
            LceState(
                content = content,
                isLoading = content == null
            )
        }.stateIn(
            viewModel = this,
            initialValue = LceState(content = null, isLoading = true)
        )

    private fun createState(
        round: VotingRound,
        recovery: VotingRecoverySnapshot?,
        isKeystone: Boolean,
        status: VoteSubmissionStatus,
    ): VoteConfirmSubmissionState {
        val weightText = recovery?.eligibleWeight?.toVotingWeightLabel() ?: "Preparing..."
        val hotkeyAddress = recovery?.hotkeyAddress ?: "Preparing..."
        val isPrepared = recovery?.eligibleWeight != null && recovery.hotkeyAddress != null
        val keystoneSignedBundles = recovery?.keystoneBundleSignatures?.size ?: 0
        val preparedBundleCount = recovery?.bundleCount ?: 0
        val hasPendingKeystoneRequest = recovery?.pendingKeystoneRequest != null
        val allKeystoneBundlesSigned = preparedBundleCount > 0 && keystoneSignedBundles >= preparedBundleCount
        val isSubmitting = status is VoteSubmissionStatus.Authorizing || status is VoteSubmissionStatus.Submitting
        val includesAuthorizationProgress = recovery?.phase?.let { phase ->
            phase == VotingRecoveryPhase.INITIALIZED ||
                phase == VotingRecoveryPhase.BUNDLES_PREPARED ||
                phase == VotingRecoveryPhase.HOTKEY_READY ||
                phase == VotingRecoveryPhase.DELEGATION_PROVED
        } ?: true
        val memo = if (isKeystone && !allKeystoneBundlesSigned) {
            if (hasPendingKeystoneRequest) {
                "Resume the pending Keystone signing request, then continue signing the remaining delegation bundles."
            } else {
                "Sign each prepared delegation bundle with Keystone before submitting your votes."
            }
        } else if (isPrepared) {
            "I am authorizing this hotkey managed by my wallet to vote on ${round.title} with $weightText."
        } else {
            "Your wallet is still preparing the voting authorization for this poll."
        }

        return VoteConfirmSubmissionState(
            status = status,
            roundTitle = stringRes(round.title),
            votingWeightZEC = stringRes(weightText),
            hotkeyAddress = stringRes(hotkeyAddress),
            isKeystoneUser = isKeystone,
            includesAuthorizationProgress = includesAuthorizationProgress,
            memo = stringRes(memo),
            ctaButton = buildButtonState(
                isPrepared = isPrepared,
                isKeystone = isKeystone,
                keystoneSignedBundles = keystoneSignedBundles,
                preparedBundleCount = preparedBundleCount,
                hasPendingKeystoneRequest = hasPendingKeystoneRequest,
                isSubmitting = isSubmitting,
                status = status
            ),
            onBack = ::onBack
        )
    }

    private fun buildButtonState(
        isPrepared: Boolean,
        isKeystone: Boolean,
        keystoneSignedBundles: Int,
        preparedBundleCount: Int,
        hasPendingKeystoneRequest: Boolean,
        isSubmitting: Boolean,
        status: VoteSubmissionStatus
    ) = when (status) {
        is VoteSubmissionStatus.Completed -> ButtonState(
            text = stringRes("Done"),
            style = ButtonStyle.PRIMARY,
            onClick = ::onDone
        )

        is VoteSubmissionStatus.Failed -> ButtonState(
            text = stringRes("Try Again"),
            style = ButtonStyle.PRIMARY,
            isEnabled = isPrepared && draftChoices.isNotEmpty(),
            onClick = if (isKeystone && keystoneSignedBundles < preparedBundleCount) ::onStartKeystoneSigning else ::onSubmit
        )

        else -> ButtonState(
            text = stringRes(
                when {
                    !isPrepared -> "Preparing vote..."
                    isKeystone && keystoneSignedBundles < preparedBundleCount ->
                        if (hasPendingKeystoneRequest) {
                            "Resume Keystone signing"
                        } else if (keystoneSignedBundles == 0) {
                            "Sign with Keystone"
                        } else {
                            "Sign bundle ${keystoneSignedBundles + 1}/$preparedBundleCount"
                        }
                    isSubmitting -> "Submitting..."
                    else -> "Submit Votes"
                }
            ),
            style = ButtonStyle.PRIMARY,
            isEnabled = isPrepared && !isSubmitting && draftChoices.isNotEmpty(),
            onClick = if (isKeystone && keystoneSignedBundles < preparedBundleCount) ::onStartKeystoneSigning else ::onSubmit
        )
    }

    private fun onStartKeystoneSigning() {
        navigationRouter.forward(SignKeystoneVotingArgs(args.roundIdHex))
    }

    private fun onSubmit() {
        if (draftChoices.isEmpty()) {
            statusFlow.value = VoteSubmissionStatus.Failed("No vote choices are available to submit.")
            return
        }
        if (statusFlow.value is VoteSubmissionStatus.Authorizing ||
            statusFlow.value is VoteSubmissionStatus.Submitting
        ) {
            return
        }

        viewModelScope.launch {
            statusFlow.value = VoteSubmissionStatus.Authorizing(progress = 0f)
            runCatching {
                submitVotes(args.roundIdHex, draftChoices, ::onSubmissionProgress)
            }.onSuccess {
                statusFlow.value = VoteSubmissionStatus.Completed
            }.onFailure { throwable ->
                Log.e(
                    "VoteConfirmSubmission",
                    "Failed to submit votes for round ${args.roundIdHex}",
                    throwable
                )
                statusFlow.value = VoteSubmissionStatus.Failed(
                    throwable.message ?: "Vote submission failed."
                )
            }
        }
    }

    private fun onSubmissionProgress(progress: VotingSubmissionProgress) {
        statusFlow.value = when (progress) {
            is VotingSubmissionProgress.Authorizing ->
                VoteSubmissionStatus.Authorizing(progress.progress)

            is VotingSubmissionProgress.Submitting ->
                VoteSubmissionStatus.Submitting(
                    current = progress.current,
                    total = progress.total,
                    progress = progress.progress
                )
        }
    }

    private suspend fun persistDraftChoices(round: VotingRound) {
        val persistedDraftChoices = draftChoices
            .filterKeys { proposalId -> round.proposals.any { proposal -> proposal.id == proposalId } }
        if (persistedDraftChoices.isNotEmpty()) {
            votingRecoveryRepository.storeDraftChoices(args.roundIdHex, persistedDraftChoices)
        }
    }

    private fun onDone() {
        viewModelScope.launch {
            val recovery = votingRecoveryRepository.get(args.roundIdHex)
            val persistedChoices = recovery
                ?.proposalSelections
                ?.mapValues { (_, selection) -> selection.choiceId }
                ?.ifEmpty { draftChoices }
                ?: draftChoices
            if (persistedChoices.isNotEmpty()) {
                votingSessionStore.restoreDraftVotes(args.roundIdHex, persistedChoices)
            }
            navigationRouter.replace(
                VoteProposalListArgs(
                    roundId = args.roundIdHex,
                    mode = VoteProposalListMode.VOTED
                )
            )
        }
    }

    private fun onBack() {
        when (statusFlow.value) {
            is VoteSubmissionStatus.Authorizing,
            is VoteSubmissionStatus.Submitting -> Unit
            else -> navigationRouter.back()
        }
    }
}

private fun Long.toVotingWeightLabel() = "%.4f ZEC".format(this / 100_000_000.0)

private fun String.toDraftChoices(): Map<Int, Int> {
    val json = JSONObject(this)
    return buildMap {
        json.keys().forEach { key ->
            put(key.toInt(), json.getInt(key))
        }
    }
}
