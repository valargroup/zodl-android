package co.electriccoin.zcash.ui.screen.voting.votingerror

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoteErrorVM(
    private val args: VoteErrorArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteErrorState>> =
        MutableStateFlow(
            LceState(
                content = VoteErrorState(
                    title = stringRes("Something went wrong"),
                    message = stringRes(VotingErrorMapper.toUserFriendlyMessage(args.message)),
                    actionButton = ButtonState(
                        text = stringRes(if (args.isRecoverable) "Try Again" else "Dismiss"),
                        style = ButtonStyle.PRIMARY,
                        onClick = if (args.isRecoverable) ::onRetry else ::onDismiss,
                    ),
                    onBack = ::onBack,
                ),
                isLoading = false,
            )
        )

    private fun onRetry() = navigationRouter.back()

    private fun onDismiss() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}

class VoteConfigErrorVM(
    private val args: VoteConfigErrorArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteConfigErrorState>> =
        MutableStateFlow(
            LceState(
                content = VoteConfigErrorState(
                    message = stringRes(
                        args.message.ifBlank {
                            "This wallet version is not compatible with the current voting round. Please update the app to participate."
                        }
                    ),
                    dismissButton = ButtonState(
                        text = stringRes("Dismiss"),
                        style = ButtonStyle.PRIMARY,
                        onClick = ::onDismiss,
                    ),
                    onBack = ::onBack,
                ),
                isLoading = false,
            )
        )

    private fun onDismiss() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}
