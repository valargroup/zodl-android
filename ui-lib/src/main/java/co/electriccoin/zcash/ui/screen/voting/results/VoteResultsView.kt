package co.electriccoin.zcash.ui.screen.voting.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
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
fun VoteResultsView(state: VoteResultsState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Coinholder Polling",
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
                    .scaffoldPadding(padding)
                    .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = state.roundTitle.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )

                if (state.roundDescription.getValue().isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.roundDescription.getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textTertiary,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringRes("Results").getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (state.isLoadingResults) {
                    Text(
                        text = stringRes("Results are still being tallied.").getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textTertiary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                state.proposals.forEach { proposal ->
                    ProposalResultCard(proposal)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                ZashiButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ZashiDimensions.Spacing.spacingMd),
                    state = state.doneButton,
                )
            }
        }
    )
}

@Composable
private fun ProposalResultCard(state: VoteProposalResultState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgPrimary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZashiDimensions.Spacing.spacingXl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                state.zipNumber?.let { zipNumber ->
                    Text(
                        text = zipNumber.getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textTertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                state.winnerLabel?.let { winner ->
                    Text(
                        text = "Winner: ${winner.getValue()}",
                        style = ZashiTypography.textSm,
                        color = optionColor(state.winnerColor),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )

            if (state.description.getValue().isNotEmpty()) {
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
            }

            state.options.forEach { option ->
                OptionResultBar(option)
            }

            Text(
                text = state.totalZec.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@Composable
private fun OptionResultBar(option: VoteOptionResultState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = option.label.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = option.amountZec.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
        LinearProgressIndicator(
            progress = { option.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = optionColor(option.color),
            trackColor = ZashiColors.Surfaces.bgSecondary,
        )
    }
}

private fun optionColor(color: VoteOptionColor): Color =
    when (color) {
        VoteOptionColor.SUPPORT -> Color(0xFF22C55E)
        VoteOptionColor.OPPOSE -> Color(0xFFEF4444)
        VoteOptionColor.ABSTAIN -> Color(0xFFF59E0B)
        VoteOptionColor.OTHER -> Color(0xFF3B82F6)
    }
