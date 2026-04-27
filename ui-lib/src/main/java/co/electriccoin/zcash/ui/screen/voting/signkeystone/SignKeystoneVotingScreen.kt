package co.electriccoin.zcash.ui.screen.voting.signkeystone

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionBottomSheet
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignKeystoneVotingScreen(args: SignKeystoneVotingArgs) {
    val vm = koinViewModel<SignKeystoneVotingVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    val bottomSheetState by vm.bottomSheetState.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack?.invoke() }
    state?.let { SignKeystoneTransactionView(it) }
    SignKeystoneTransactionBottomSheet(state = bottomSheetState)
}

@Serializable
data class SignKeystoneVotingArgs(
    val roundIdHex: String
)
