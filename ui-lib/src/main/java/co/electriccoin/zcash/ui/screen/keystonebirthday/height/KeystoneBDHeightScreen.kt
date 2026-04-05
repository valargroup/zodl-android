package co.electriccoin.zcash.ui.screen.keystonebirthday.height

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.restore.height.RestoreBDHeightView
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KeystoneBDHeightScreen(args: KeystoneBDHeightArgs) {
    val vm = koinViewModel<KeystoneBDHeightVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.onBack() }
    RestoreBDHeightView(state)
}
