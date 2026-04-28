package co.electriccoin.zcash.ui.screen.voting.signkeystone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
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
    val isLoading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    BackHandler {
        if (state != null) {
            state?.onBack?.invoke()
        } else {
            vm.onScreenBack()
        }
    }

    when {
        state != null -> SignKeystoneTransactionView(requireNotNull(state))
        isLoading -> SignKeystoneVotingLoadingView(onBack = vm::onScreenBack)
        error != null -> SignKeystoneVotingErrorView(
            message = requireNotNull(error),
            onBack = vm::onScreenBack,
            onRetry = vm::onRetry
        )
    }

    SignKeystoneTransactionBottomSheet(state = bottomSheetState)
}

@Serializable
data class SignKeystoneVotingArgs(
    val roundIdHex: String
)

@Composable
private fun SignKeystoneVotingLoadingView(onBack: () -> Unit) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Confirmation",
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                }
            )
        }
    ) { padding ->
        CircularScreenProgressIndicator(
            modifier = Modifier.scaffoldPadding(padding)
        )
    }
}

@Composable
private fun SignKeystoneVotingErrorView(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Confirmation",
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldPadding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Unable to prepare Keystone signing",
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = message,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textSecondary
            )
            ZashiButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                state = ButtonState(
                    text = stringRes("Try Again"),
                    onClick = onRetry
                )
            )
        }
    }
}
