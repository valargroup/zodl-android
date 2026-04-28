package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoteWalletSyncingVM(
    private val args: VoteWalletSyncingArgs,
    private val votingConfigRepository: VotingConfigRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    init {
        viewModelScope.launch {
            votingConfigRepository.get()
        }
    }

    val state: StateFlow<LceState<VoteWalletSyncingState>> =
        combine(
            votingConfigRepository.currentConfig,
            synchronizerProvider.synchronizer,
        ) { config, synchronizer ->
            val snapshotHeight = config
                ?.takeIf { snapshot -> snapshot.session.voteRoundId.toHex() == args.roundId }
                ?.session
                ?.snapshotHeight

            if (snapshotHeight == null || synchronizer == null) {
                LceState(content = null, isLoading = true)
            } else {
                val scannedHeight = synchronizer.fullyScannedHeight.value?.value ?: 0L
                val progress = (scannedHeight.coerceAtMost(snapshotHeight).toFloat() / snapshotHeight.toFloat())
                    .coerceIn(0f, 1f)
                val isSynced = scannedHeight >= snapshotHeight

                LceState(
                    content = VoteWalletSyncingState(
                        title = stringRes("Syncing your wallet"),
                        body = stringRes(
                            "Your wallet needs to scan up to block $snapshotHeight before you can vote. Currently at block $scannedHeight."
                        ),
                        progressLabel = stringRes(
                            if (isSynced) {
                                "100% complete"
                            } else {
                                "${(progress * 100).toInt()}% complete"
                            }
                        ),
                        progress = progress,
                        isSynced = isSynced,
                        continueButton = ButtonState(
                            text = stringRes("Continue"),
                            style = ButtonStyle.PRIMARY,
                            isEnabled = isSynced,
                            onClick = ::onContinue
                        ),
                        onBack = ::onBack,
                    ),
                    isLoading = false,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = LceState(content = null, isLoading = true)
        )

    private fun onContinue() = navigationRouter.replace(VoteProposalListArgs(roundId = args.roundId))

    private fun onBack() = navigationRouter.back()
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
