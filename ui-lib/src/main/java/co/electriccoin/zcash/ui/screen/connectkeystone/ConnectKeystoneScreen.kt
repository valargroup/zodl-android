package co.electriccoin.zcash.ui.screen.connectkeystone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectKeystoneScreen() {
    val vm = koinViewModel<ConnectKeystoneVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    ConnectKeystoneView(state)
}

@Serializable
object ConnectKeystoneArgs
