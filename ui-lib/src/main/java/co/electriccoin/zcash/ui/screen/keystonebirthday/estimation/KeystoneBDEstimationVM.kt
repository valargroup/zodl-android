package co.electriccoin.zcash.ui.screen.keystonebirthday.estimation

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.InitializeException
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.screen.restore.estimation.RestoreBDEstimationState
import co.electriccoin.zcash.ui.screen.restore.info.SeedInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeystoneBDEstimationVM(
    private val args: KeystoneBDEstimationArgs,
    private val navigationRouter: NavigationRouter,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
) : ViewModel() {
    private val isImporting = MutableStateFlow(false)

    val state: StateFlow<RestoreBDEstimationState> =
        isImporting
            .let { flow ->
                combine(flow, flow) { importing, _ -> createState(importing) }
            }
            .stateIn(
                scope = viewModelScope,
                started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
                initialValue = createState(false)
            )

    private fun createState(isImporting: Boolean) =
        RestoreBDEstimationState(
            title = stringRes(R.string.restore_title),
            subtitle = stringRes(R.string.restore_bd_estimation_subtitle),
            message = stringRes(R.string.restore_bd_estimation_message),
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoButtonClick,
                ),
            onBack = ::onBack,
            text = stringResByNumber(args.blockHeight, 0),
            copy =
                ButtonState(
                    text = stringRes(R.string.restore_bd_estimation_copy),
                    icon = R.drawable.ic_copy,
                    onClick = ::onCopyClick
                ),
            restore =
                ButtonState(
                    text = stringRes(R.string.restore_bd_estimation_restore),
                    onClick = ::onRestoreClick,
                    isEnabled = !isImporting,
                    isLoading = isImporting,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
        )

    private fun onCopyClick() {
        copyToClipboard(value = args.blockHeight.toString())
    }

    private fun onRestoreClick() {
        viewModelScope.launch {
            if (isImporting.value) return@launch
            try {
                isImporting.update { true }
                val accounts = parseKeystoneUrToZashiAccounts(args.ur)
                val account = accounts.accounts[args.accountIndex]
                createKeystoneAccount(accounts, account, birthdayHeight = args.blockHeight)
            } catch (e: InitializeException.ImportAccountException) {
                Twig.error(e) { "Error importing keystone account" }
            } finally {
                isImporting.update { false }
            }
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun onInfoButtonClick() = navigationRouter.forward(SeedInfo)
}
