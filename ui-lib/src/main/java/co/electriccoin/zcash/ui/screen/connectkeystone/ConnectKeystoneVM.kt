package co.electriccoin.zcash.ui.screen.connectkeystone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.connectkeystone.explainer.KeystoneHardwareWalletExplainerState
import co.electriccoin.zcash.ui.screen.scankeystone.ScanKeystoneSignInRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ConnectKeystoneVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val showExplainer = MutableStateFlow(false)

    val state: StateFlow<ConnectKeystoneState> =
        showExplainer
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(false),
            )

    private fun createState(showExplainer: Boolean) =
        ConnectKeystoneState(
            onBackClick = ::onBack,
            onContinueClick = ::onContinue,
            onViewKeystoneTutorialClicked = ::onViewKeystoneTutorial,
            explainer =
                if (showExplainer) {
                    KeystoneHardwareWalletExplainerState(onBack = ::onExplainerBack)
                } else {
                    null
                },
        )

    private fun onBack() = navigationRouter.back()

    private fun onContinue() = navigationRouter.forward(ScanKeystoneSignInRequest)

    private fun onViewKeystoneTutorial() {
        showExplainer.value = true
    }

    private fun onExplainerBack() {
        showExplainer.value = false
    }
}
