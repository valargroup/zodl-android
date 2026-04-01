package co.electriccoin.zcash.ui.screen.connectkeystone.wbh

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction.KeystoneFirstTransactionArgs
import co.electriccoin.zcash.ui.screen.restore.height.RestoreBDHeightState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeystoneWBHVM(
    private val args: KeystoneWBHArgs,
    parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val accounts = parseKeystoneUrToZashiAccounts(args.ur)
    private val blockHeightText = MutableStateFlow(NumberTextFieldInnerState())
    private val isCreatingAccount = MutableStateFlow(false)

    val state: StateFlow<RestoreBDHeightState> =
        combine(blockHeightText, isCreatingAccount) { text, isCreating ->
            createState(text, isCreating)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(blockHeightText.value, false),
        )

    private fun createState(
        blockHeight: NumberTextFieldInnerState,
        isCreating: Boolean,
    ): RestoreBDHeightState {
        val isHigherThanSaplingActivationHeight =
            blockHeight.amount
                ?.let { it.toLong() >= VersionInfo.NETWORK.saplingActivationHeight.value }
                ?: false
        val isValid = !blockHeight.innerTextFieldState.value.isEmpty() && isHigherThanSaplingActivationHeight

        return RestoreBDHeightState(
            title = stringRes(R.string.keystone_wbh_title),
            subtitle = stringRes(R.string.keystone_wbh_subtitle),
            message = stringRes(R.string.keystone_wbh_message),
            textFieldTitle = stringRes(R.string.restore_bd_text_field_title),
            textFieldHint = stringRes(R.string.restore_bd_text_field_hint),
            textFieldNote = stringRes(R.string.restore_bd_text_field_note),
            onBack = ::onBack,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = {},
                ),
            restore =
                ButtonState(
                    text = stringRes(R.string.keystone_wbh_confirm_button),
                    onClick = ::onConfirmClick,
                    isEnabled = isValid && !isCreating,
                    isLoading = isCreating,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                ),
            estimate =
                ButtonState(
                    text = stringRes(R.string.keystone_wbh_estimate_button),
                    onClick = ::onEstimateClick,
                    isEnabled = !isCreating,
                ),
            blockHeight = NumberTextFieldState(innerState = blockHeight, onValueChange = ::onValueChanged),
        )
    }

    private fun onConfirmClick() {
        if (isCreatingAccount.value) return
        val heightValue = blockHeightText.value.amount?.toLong() ?: return
        val account = accounts.accounts.firstOrNull() ?: return
        viewModelScope.launch {
            try {
                isCreatingAccount.update { true }
                createKeystoneAccount(
                    accounts,
                    account,
                    BlockHeight.new(heightValue),
                )
            } catch (e: InitializeException.ImportAccountException) {
                Twig.error(e) { "Error importing Keystone account" }
            } finally {
                isCreatingAccount.update { false }
            }
        }
    }

    private fun onEstimateClick() = navigationRouter.forward(KeystoneFirstTransactionArgs(args.ur))

    private fun onBack() = navigationRouter.back()

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }
}
