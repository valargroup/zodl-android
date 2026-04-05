package co.electriccoin.zcash.ui.screen.keystonebirthday.estimation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.restore.estimation.RestoreBDEstimationView
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KeystoneBDEstimationScreen(args: KeystoneBDEstimationArgs) {
    val vm = koinViewModel<KeystoneBDEstimationVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.onBack() }
    RestoreBDEstimationView(state)
}
