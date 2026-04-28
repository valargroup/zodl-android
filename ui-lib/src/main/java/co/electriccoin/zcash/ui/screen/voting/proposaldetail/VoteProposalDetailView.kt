package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun VoteProposalDetailView(state: VoteProposalDetailState) {
    BackHandler { state.onBack() }

    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scaffoldPadding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                ) {
                    VerticalSpacer(24.dp)

                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.header6,
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (state.description.getValue().isNotEmpty()) {
                        Spacer(16.dp)
                        Text(
                            text = state.description.getValue(),
                            style = ZashiTypography.textMd,
                            color = ZashiColors.Text.textSecondary,
                        )
                    }

                    state.forumUrl?.let { forumUrl ->
                        Spacer(16.dp)
                        ForumLinkRow(url = forumUrl)
                    }

                    VerticalSpacer(24.dp)
                }

                BottomSection(state = state)
            }
        }
    )

    if (state.showUnansweredSheet) {
        UnansweredBottomSheet(state = state)
    }
}

@Composable
private fun AppBar(state: VoteProposalDetailState) {
    ZashiSmallTopAppBar(
        title = state.positionLabel.getValue(),
        navigationAction = {
            ZashiTopAppBarCloseNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors = ZcashTheme.colors.topAppBarColors orDark
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            )
    )
}

@Composable
private fun ForumLinkRow(url: String) {
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = { uriHandler.openUri(url) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ZashiColors.Surfaces.bgTertiary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = ZashiColors.Text.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(12.dp)

            Text(
                text = stringRes("View Forum Discussion").getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ZashiColors.Text.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BottomSection(state: VoteProposalDetailState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
            .padding(bottom = ZashiDimensions.Spacing.spacingMd)
    ) {
        VoteOptions(options = state.options)
        if (!state.isLocked) {
            Spacer(20.dp)
            NavigationButtons(state = state)
        }
    }
}

@Composable
private fun VoteOptions(options: List<VoteVoteOptionRowState>) {
    Column {
        options.forEachIndexed { index, option ->
            VoteOptionRow(option = option)
            if (index < options.lastIndex) {
                Spacer(12.dp)
            }
        }
    }
}

@Composable
private fun VoteOptionRow(option: VoteVoteOptionRowState) {
    val selectedBg = option.color.toComposeBgColor()
    val backgroundColor = if (option.isSelected) selectedBg else ZashiColors.Surfaces.bgSecondary
    val textColor = if (option.isSelected) ZashiColors.Surfaces.bgPrimary else ZashiColors.Text.textPrimary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        onClick = { if (!option.isLocked) option.onSelect() },
        enabled = !option.isLocked,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = option.label.getValue(),
                style = ZashiTypography.textMd,
                color = textColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            CheckboxIndicator(isSelected = option.isSelected)
        }
    }
}

@Composable
private fun CheckboxIndicator(isSelected: Boolean) {
    if (isSelected) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = ZashiColors.Text.textPrimary,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ZashiColors.Surfaces.bgPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Transparent,
            modifier = Modifier.size(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ZashiColors.Surfaces.strokeSecondary)
        ) {}
    }
}

@Composable
private fun VoteOptionDisplayColor.toComposeBgColor(): Color =
    when (this) {
        VoteOptionDisplayColor.SUPPORT -> ZashiColors.Utility.SuccessGreen.utilitySuccess500
        VoteOptionDisplayColor.OPPOSE -> ZashiColors.Utility.ErrorRed.utilityError500
        VoteOptionDisplayColor.ABSTAIN -> ZashiColors.Utility.HyperBlue.utilityBlueDark700
        VoteOptionDisplayColor.PURPLE -> ZashiColors.Utility.Purple.utilityPurple500
        VoteOptionDisplayColor.WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange500
        VoteOptionDisplayColor.INDIGO -> ZashiColors.Utility.Indigo.utilityIndigo500
        VoteOptionDisplayColor.BRAND -> ZashiColors.Utility.Espresso.utilityEspresso500
        VoteOptionDisplayColor.GRAY -> ZashiColors.Utility.Gray.utilityGray500
        VoteOptionDisplayColor.INDIGO_DARK -> ZashiColors.Utility.Indigo.utilityIndigo700
    }

@Composable
private fun NavigationButtons(state: VoteProposalDetailState) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ZashiButton(
            modifier = Modifier.weight(1f),
            state = ButtonState(
                text = stringRes("Back"),
                style = ButtonStyle.SECONDARY,
                onClick = state.onBack
            )
        )

        if (!state.isEditingFromReview) {
            Spacer(12.dp)
            ZashiButton(
                modifier = Modifier.weight(1f),
                state = ButtonState(
                    text = stringRes("Next"),
                    style = ButtonStyle.PRIMARY,
                    onClick = state.onNext
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnansweredBottomSheet(state: VoteProposalDetailState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = state.onDismissUnanswered,
        sheetState = sheetState,
        containerColor = ZashiColors.Surfaces.bgPrimary,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ZashiColors.Utility.ErrorRed.utilityError500.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = ZashiColors.Utility.ErrorRed.utilityError500,
                    modifier = Modifier.padding(12.dp)
                )
            }

            VerticalSpacer(16.dp)

            Text(
                text = stringRes("Unanswered Questions").getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )

            VerticalSpacer(8.dp)

            val countText = if (state.unansweredCount == 1) "1 question" else "${state.unansweredCount} questions"
            val pronounText = if (state.unansweredCount == 1) "this question" else "these questions"

            Text(
                text = "You have not responded to $countText. Confirm to abstain from $pronounText or go back to respond.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            VerticalSpacer(24.dp)

            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = ButtonState(
                    text = stringRes("Confirm"),
                    style = ButtonStyle.SECONDARY,
                    onClick = state.onConfirmUnanswered
                )
            )

            VerticalSpacer(12.dp)

            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = ButtonState(
                    text = stringRes("Go back"),
                    style = ButtonStyle.PRIMARY,
                    onClick = state.onDismissUnanswered
                )
            )
        }
    }
}
