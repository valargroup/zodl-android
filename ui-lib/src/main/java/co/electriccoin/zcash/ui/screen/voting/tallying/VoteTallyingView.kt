package co.electriccoin.zcash.ui.screen.voting.tallying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun VoteTallyingView(state: VoteTallyingState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Governance",
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = state.onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                },
                colors = ZcashTheme.colors.topAppBarColors orDark
                    ZcashTheme.colors.topAppBarColors.copyColors(containerColor = Color.Transparent)
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = ZashiColors.Btns.Primary.btnPrimaryBg.copy(alpha = 0.12f)
                    ) {}
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = ZashiColors.Btns.Primary.btnPrimaryBg,
                        strokeWidth = 3.dp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringRes("Votes Closed").getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringRes(
                        "Results are being tallied. The election authority is decrypting the aggregate vote totals."
                    ).getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = ZashiColors.Btns.Primary.btnPrimaryBg,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringRes("Tallying").getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textTertiary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZashiDimensions.Spacing.spacingMd),
                    shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
                    color = ZashiColors.Surfaces.bgPrimary,
                    shadowElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(ZashiDimensions.Spacing.spacingXl),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailRow(label = "Round", value = state.roundTitle.getValue())
                        DetailRow(label = "Ended", value = state.endedLabel.getValue())
                        DetailRow(label = "Proposals", value = state.proposalCount.getValue())
                    }
                }
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
