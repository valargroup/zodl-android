package co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction.estimation

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetResyncDataFromHeightUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.restore.estimation.RestoreBDEstimationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

class KeystoneFirstTransactionEstimationVM(
    private val args: KeystoneFirstTransactionEstimationArgs,
    parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val getResyncDataFromHeight: GetResyncDataFromHeightUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val accounts = parseKeystoneUrToZashiAccounts(args.ur)
    private val isCreatingAccount = MutableStateFlow(false)

    private val yearMonthFlow =
        flow {
            emit(getResyncDataFromHeight(BlockHeight.new(args.blockHeight)))
        }

    val state: StateFlow<RestoreBDEstimationState?> =
        combine(yearMonthFlow, isCreatingAccount) { yearMonth, isCreating ->
            createState(yearMonth, isCreating)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null,
        )

    private fun createState(
        yearMonth: YearMonth,
        isCreating: Boolean,
    ) = RestoreBDEstimationState(
        title = stringRes(R.string.keystone_first_transaction_estimation_title),
        subtitle = stringRes(R.string.keystone_first_transaction_estimation_subtitle),
        message =
            styledStringResource(
                resource = R.string.keystone_first_transaction_estimation_message,
                style =
                    StyledStringStyle(
                        color = StringResourceColor.TERTIARY,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    ),
                stringRes(yearMonth).withStyle(
                    StyledStringStyle(
                        color = StringResourceColor.PRIMARY,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                ),
                stringResByNumber(args.blockHeight, 0).withStyle(
                    StyledStringStyle(color = StringResourceColor.TERTIARY)
                ),
            ),
        dialogButton = null,
        onBack = ::onBack,
        text = stringResByNumber(args.blockHeight, 0),
        copy =
            ButtonState(
                text = stringRes(R.string.restore_bd_estimation_copy),
                icon = R.drawable.ic_copy,
                onClick = {},
            ),
        restore =
            ButtonState(
                text = stringRes(R.string.keystone_first_transaction_estimation_confirm),
                isLoading = isCreating,
                onClick = ::onConfirmClick,
                hapticFeedbackType = HapticFeedbackType.Confirm,
            ),
    )

    private fun onConfirmClick() {
        if (isCreatingAccount.value) return
        val account = accounts.accounts.firstOrNull() ?: return
        viewModelScope.launch {
            try {
                isCreatingAccount.update { true }
                createKeystoneAccount(
                    accounts,
                    account,
                    BlockHeight.new(args.blockHeight),
                )
            } catch (e: InitializeException.ImportAccountException) {
                Twig.error(e) { "Error importing Keystone account" }
            } finally {
                isCreatingAccount.update { false }
            }
        }
    }

    private fun onBack() = navigationRouter.back()
}
