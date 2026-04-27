package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VoteConfirmSubmissionVM(
    private val args: VoteConfirmSubmissionArgs,
    votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    prepareVotingRound: PrepareVotingRoundUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val statusFlow = MutableStateFlow<VoteSubmissionStatus>(VoteSubmissionStatus.Idle)

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
        val memo = if (isPrepared) {
            "I am authorizing this hotkey managed by my wallet to vote on " +
                "${round.title} with $weightText."
        } else {
            "Your wallet is still preparing the voting authorization for this poll."
        }

        return VoteConfirmSubmissionState(
            status = status,
            roundTitle = stringRes(round.title),
            votingWeightZEC = stringRes(weightText),
            hotkeyAddress = stringRes(hotkeyAddress),
            isKeystoneUser = isKeystone,
            memo = stringRes(memo),
            ctaButton = ButtonState(
                text = stringRes(if (isPrepared) "Submit Votes" else "Preparing vote..."),
                style = ButtonStyle.PRIMARY,
                isEnabled = false,
                onClick = {}
            ),
            onBack = ::onBack
        )
    }

    private fun onBack() = navigationRouter.back()
}

private fun Long.toVotingWeightLabel() = "%.4f ZEC".format(this / 100_000_000.0)
