package co.electriccoin.zcash.ui.screen.connectkeystone.neworactive

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction.KeystoneFirstTransactionArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeystoneNewOrActiveVM(
    private val args: KeystoneNewOrActiveArgs,
    parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val accounts = parseKeystoneUrToZashiAccounts(args.ur)
    private val isCreatingAccount = MutableStateFlow(false)

    val state: StateFlow<KeystoneNewOrActiveState> =
        isCreatingAccount
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(false),
            )

    private fun createState(isCreating: Boolean) =
        KeystoneNewOrActiveState(
            subtitle = stringRes(R.string.keystone_new_or_active_subtitle),
            message = stringRes(R.string.keystone_new_or_active_message),
            newDevice =
                ButtonState(
                    text = stringRes(R.string.keystone_new_device_button),
                    isLoading = isCreating,
                    onClick = ::onNewDeviceClick,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                ),
            activeDevice =
                ButtonState(
                    text = stringRes(R.string.keystone_active_device_button),
                    isEnabled = !isCreating,
                    onClick = ::onActiveDeviceClick,
                ),
            onBack = ::onBack,
        )

    private fun onNewDeviceClick() {
        if (isCreatingAccount.value) return
        val account = accounts.accounts.firstOrNull() ?: return
        viewModelScope.launch {
            try {
                isCreatingAccount.update { true }
                createKeystoneAccount(accounts, account, birthday = null)
            } catch (e: InitializeException.ImportAccountException) {
                Twig.error(e) { "Error importing Keystone account" }
            } finally {
                isCreatingAccount.update { false }
            }
        }
    }

    private fun onActiveDeviceClick() {
        if (isCreatingAccount.value) return
        navigationRouter.forward(KeystoneFirstTransactionArgs(args.ur))
    }

    private fun onBack() {
        if (!isCreatingAccount.value) {
            navigationRouter.back()
        }
    }
}
