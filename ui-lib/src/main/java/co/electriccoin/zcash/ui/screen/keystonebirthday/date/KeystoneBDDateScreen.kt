package co.electriccoin.zcash.ui.screen.keystonebirthday.date

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.restore.date.RestoreBDDateView
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KeystoneBDDateScreen(args: KeystoneBDDateArgs) {
    val vm = koinViewModel<KeystoneBDDateVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state != null) { state?.onBack?.invoke() }
    state?.let { RestoreBDDateView(it) }
}
