package co.electriccoin.zcash.ui.screen.transactiondetail.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.swap.detail.InfoBoxState
import co.electriccoin.zcash.ui.screen.swap.detail.InfoBoxTheme

@Composable
fun SwapInfoBox(state: InfoBoxState) {
val containerColor =
        when (state.theme) {
            InfoBoxTheme.WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange50
            InfoBoxTheme.INFO -> ZashiColors.Utility.HyperBlue.utilityBlueDark50
        }
    val contentColor =
        when (state.theme) {
            InfoBoxTheme.WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange800
            InfoBoxTheme.INFO -> ZashiColors.Utility.HyperBlue.utilityBlueDark800
        }
    val titleColor =
        when (state.theme) {
            InfoBoxTheme.WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange700
            InfoBoxTheme.INFO -> ZashiColors.Utility.HyperBlue.utilityBlueDark700
        }
    val iconColor =
        when (state.theme) {
            InfoBoxTheme.WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange500
            InfoBoxTheme.INFO -> ZashiColors.Utility.HyperBlue.utilityBlueDark500
        }

    ZashiCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row {
            Image(
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(iconColor)
            )
            Spacer(12.dp)
            Column {
                Spacer(2.dp)
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                Spacer(8.dp)
                Text(
                    text = state.message.getValue(),
                    style = ZashiTypography.textXs,
                    color = contentColor
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun WarningPreview() =
    ZcashTheme {
        SwapInfoBox(
            state =
                InfoBoxState(
                    title = stringRes("Swap Processing"),
                    message =
                        stringRes(
                            "Your swap has been delayed. Swaps typically complete in minutes but may take longer " +
                                "depending on market conditions."
                        ),
                    theme = InfoBoxTheme.WARNING
                )
        )
    }

@PreviewScreens
@Composable
private fun InfoPreview() =
    ZcashTheme {
        SwapInfoBox(
            state =
                InfoBoxState(
                    title = stringRes("Swap Processing"),
                    message =
                        stringRes(
                            "Your swap has been delayed. Swaps typically complete in minutes but may take longer " +
                                "depending on market conditions."
                        ),
                    theme = InfoBoxTheme.INFO
                )
        )
    }
