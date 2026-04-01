package co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction.estimation.KeystoneFirstTransactionEstimationArgs
import co.electriccoin.zcash.ui.screen.connectkeystone.wbh.KeystoneWBHArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.toKotlinInstant

class KeystoneFirstTransactionVM(
    private val args: KeystoneFirstTransactionArgs,
    private val navigationRouter: NavigationRouter,
    private val application: Application,
) : ViewModel() {
    private val selection = MutableStateFlow(YearMonth.now())

    val state: StateFlow<KeystoneFirstTransactionState> =
        selection
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(selection.value),
            )

    private fun createState(yearMonth: YearMonth) =
        KeystoneFirstTransactionState(
            title = stringRes(R.string.keystone_first_transaction_title),
            subtitle = stringRes(R.string.keystone_first_transaction_subtitle),
            message = stringRes(R.string.keystone_first_transaction_message),
            note = stringRes(R.string.keystone_first_transaction_note),
            selection = yearMonth,
            next =
                ButtonState(
                    text = stringRes(R.string.keystone_first_transaction_next),
                    onClick = { onEstimateClick(yearMonth) },
                ),
            enterBlockHeight =
                ButtonState(
                    text = stringRes(R.string.keystone_first_transaction_enter_height),
                    onClick = ::onEnterBlockHeightClick,
                ),
            onBack = ::onBack,
            onInfo = {},
            onYearMonthChange = ::onYearMonthChange,
        )

    private fun onEstimateClick(yearMonth: YearMonth) {
        viewModelScope.launch {
            val instant =
                yearMonth
                    .atDay(1)
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toKotlinInstant()
            val bday =
                SdkSynchronizer.estimateBirthdayHeight(
                    context = application,
                    date = instant,
                    network = VersionInfo.NETWORK,
                )
            navigationRouter.forward(
                KeystoneFirstTransactionEstimationArgs(
                    ur = args.ur,
                    blockHeight = bday.value,
                )
            )
        }
    }

    private fun onEnterBlockHeightClick() = navigationRouter.forward(KeystoneWBHArgs(args.ur))

    private fun onBack() = navigationRouter.back()

    private fun onYearMonthChange(yearMonth: YearMonth) = selection.update { yearMonth }
}
