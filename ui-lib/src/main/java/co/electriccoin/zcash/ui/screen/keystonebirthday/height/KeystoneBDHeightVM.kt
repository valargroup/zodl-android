package co.electriccoin.zcash.ui.screen.keystonebirthday.height

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.InitializeException
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
import co.electriccoin.zcash.ui.screen.keystonebirthday.date.KeystoneBDDateArgs
import co.electriccoin.zcash.ui.screen.restore.height.RestoreBDHeightState
import co.electriccoin.zcash.ui.screen.restore.info.SeedInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeystoneBDHeightVM(
    private val args: KeystoneBDHeightArgs,
    private val navigationRouter: NavigationRouter,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
) : ViewModel() {
    private val blockHeightText = MutableStateFlow(NumberTextFieldInnerState())
    private val isImporting = MutableStateFlow(false)

    val state: StateFlow<RestoreBDHeightState> =
        combine(blockHeightText, isImporting) { text, importing ->
            createState(text, importing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(blockHeightText.value, false)
        )

    private fun createState(
        blockHeight: NumberTextFieldInnerState,
        isImporting: Boolean
    ): RestoreBDHeightState {
        val isHigherThanSaplingActivationHeight =
            blockHeight
                .amount
                ?.let {
                    it.toLong() >= VersionInfo.NETWORK.saplingActivationHeight.value
                }
                ?: false
        val isValid = !blockHeight.innerTextFieldState.value.isEmpty() && isHigherThanSaplingActivationHeight

        return RestoreBDHeightState(
            title = stringRes(R.string.restore_title),
            subtitle = stringRes(R.string.restore_bd_subtitle),
            message = stringRes(R.string.restore_bd_message),
            textFieldTitle = stringRes(R.string.restore_bd_text_field_title),
            textFieldHint = stringRes(R.string.restore_bd_text_field_hint),
            textFieldNote = stringRes(R.string.restore_bd_text_field_note),
            onBack = ::onBack,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoButtonClick,
                ),
            restore =
                ButtonState(
                    stringRes(R.string.restore_bd_restore_btn),
                    onClick = ::onRestoreClick,
                    isEnabled = isValid && !isImporting,
                    isLoading = isImporting,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
            estimate = ButtonState(stringRes(R.string.restore_bd_height_btn), onClick = ::onEstimateClick),
            blockHeight = NumberTextFieldState(innerState = blockHeight, onValueChange = ::onValueChanged)
        )
    }

    private fun onEstimateClick() {
        navigationRouter.forward(KeystoneBDDateArgs(ur = args.ur, accountIndex = args.accountIndex))
    }

    private fun onRestoreClick() {
        viewModelScope.launch {
            if (isImporting.value) return@launch
            try {
                isImporting.update { true }
                val accounts = parseKeystoneUrToZashiAccounts(args.ur)
                val account = accounts.accounts[args.accountIndex]
                val height = blockHeightText.value.amount?.toLong() ?: return@launch
                createKeystoneAccount(accounts, account, birthdayHeight = height)
            } catch (e: InitializeException.ImportAccountException) {
                Twig.error(e) { "Error importing keystone account" }
            } finally {
                isImporting.update { false }
            }
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun onInfoButtonClick() = navigationRouter.forward(SeedInfo)

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }
}
