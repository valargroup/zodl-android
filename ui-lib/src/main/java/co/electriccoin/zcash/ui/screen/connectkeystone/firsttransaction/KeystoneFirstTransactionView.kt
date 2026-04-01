package co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiYearMonthWheelDatePicker
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import java.time.YearMonth

@Composable
fun KeystoneFirstTransactionView(state: KeystoneFirstTransactionState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
                regularActions = {
                    ZashiIconButton(
                        state =
                            IconButtonState(
                                icon = R.drawable.ic_help,
                                onClick = state.onInfo,
                            ),
                        modifier = Modifier.size(40.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding),
        ) {
            Image(
                modifier = Modifier.height(32.dp),
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.image_keystone),
                contentDescription = null,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = state.subtitle.getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(24.dp))

            ZashiYearMonthWheelDatePicker(
                selection = state.selection,
                onSelectionChange = state.onYearMonthChange,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color = ZashiColors.Utility.Indigo.utilityIndigo700),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = state.note.getValue(),
                    style = ZashiTypography.textXs,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Utility.Indigo.utilityIndigo700,
                )
            }

            Spacer(Modifier.height(24.dp))

            ZashiButton(
                state = state.enterBlockHeight,
                modifier = Modifier.fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
            )

            Spacer(Modifier.height(12.dp))

            ZashiButton(
                state = state.next,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        KeystoneFirstTransactionView(
            state =
                KeystoneFirstTransactionState(
                    title = stringRes("Connect Hardware Wallet"),
                    subtitle = stringRes("First Wallet Transaction"),
                    message = stringRes("Select the approximate date of your first received transaction."),
                    note = stringRes("If you're not sure, choose an earlier date."),
                    selection = YearMonth.now(),
                    next = ButtonState(stringRes("Next")) {},
                    enterBlockHeight = ButtonState(stringRes("Enter block height")) {},
                    onBack = {},
                    onInfo = {},
                    onYearMonthChange = {},
                )
        )
    }
