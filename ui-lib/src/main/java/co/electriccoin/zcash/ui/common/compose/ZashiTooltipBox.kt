package co.electriccoin.zcash.ui.common.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

@Composable
@ExperimentalMaterial3Api
fun ZashiTooltipBox(
    tooltip: @Composable TooltipScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: TooltipState =
        rememberTooltipState(
            isPersistent = true,
            initialIsVisible = LocalInspectionMode.current
        ),
    positionProvider: PopupPositionProvider =
        rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below,
            8.dp
        ),
    focusable: Boolean = true,
    enableUserInput: Boolean = true,
    anchor: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = positionProvider,
        tooltip = tooltip,
        state = state,
        modifier = modifier,
        focusable = focusable,
        enableUserInput = enableUserInput,
        content = anchor
    )
}
