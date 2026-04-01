package co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction.estimation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.restore.estimation.RestoreBDEstimationView
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KeystoneFirstTransactionEstimationScreen(args: KeystoneFirstTransactionEstimationArgs) {
    val vm = koinViewModel<KeystoneFirstTransactionEstimationVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state != null) { state?.onBack?.invoke() }
    state?.let { RestoreBDEstimationView(it) }
}
