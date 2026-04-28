package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun VoteConfirmSubmissionView(state: VoteConfirmSubmissionState) {
    val navTitle = when (state.status) {
        is VoteSubmissionStatus.Idle -> "Confirmation"
        else -> "Submission"
    }

    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = navTitle,
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = state.onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                },
                colors = ZcashTheme.colors.topAppBarColors orDark
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    )
            )
        },
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
                    HeaderSection(state)
                    VerticalSpacer(24.dp)
                    DetailsCard(state)
                    if (state.status is VoteSubmissionStatus.Authorizing ||
                        state.status is VoteSubmissionStatus.Submitting
                    ) {
                        VerticalSpacer(16.dp)
                        Text(
                            text = "Vote submission is in progress, please don't leave this " +
                                "screen until it is finished.",
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textSecondary,
                        )
                    }
                    VerticalSpacer(24.dp)
                }

                BottomSection(state)
            }
        }
    )
}

@Composable
private fun HeaderSection(state: VoteConfirmSubmissionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WalletHeaderIcons(
            isKeystone = state.isKeystoneUser,
            showCheckmark = state.status is VoteSubmissionStatus.Completed,
        )
        Spacer(24.dp)
        Text(
            text = headerTitle(state.status),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        Text(
            text = headerSubtitle(state),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
        )
    }
}

private fun headerTitle(status: VoteSubmissionStatus) = when (status) {
    is VoteSubmissionStatus.Idle -> "Confirm & Submit"
    is VoteSubmissionStatus.Authorizing, is VoteSubmissionStatus.Submitting -> "Submitting vote..."
    is VoteSubmissionStatus.Completed -> "Submission Confirmed!"
    is VoteSubmissionStatus.Failed -> "Submission Failed"
}

private fun headerSubtitle(state: VoteConfirmSubmissionState) = when (val status = state.status) {
    is VoteSubmissionStatus.Idle ->
        if (state.isKeystoneUser) {
            "Review before signing the voting authorization with your Keystone. " +
                "This is final. Your vote will be published and cannot be changed."
        } else {
            "Review before confirming the voting authorization. " +
                "This is final. Your vote will be published and cannot be changed."
        }

    is VoteSubmissionStatus.Authorizing, is VoteSubmissionStatus.Submitting ->
        "Vote submission is in progress, please don't leave this screen until it is finished."

    is VoteSubmissionStatus.Completed ->
        "Your vote was successfully published and cannot be changed."

    is VoteSubmissionStatus.Failed -> status.error
}

@Composable
private fun WalletHeaderIcons(
    isKeystone: Boolean,
    showCheckmark: Boolean
) {
    Box(contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = CircleShape,
            color = ZashiColors.Text.textPrimary,
            modifier = Modifier.size(48.dp)
        ) {
            if (isKeystone) {
                Icon(
                    painter = painterResource(R.drawable.ic_item_keystone),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.zashi_logo_without_text),
                    contentDescription = null,
                    tint = ZashiColors.Surfaces.bgPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = if (showCheckmark) {
                ZashiColors.Utility.SuccessGreen.utilitySuccess500.copy(alpha = 0.15f)
            } else {
                ZashiColors.Surfaces.bgTertiary
            },
            modifier = Modifier
                .size(48.dp)
                .offset(x = 36.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (showCheckmark) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = ZashiColors.Utility.SuccessGreen.utilitySuccess500,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = ZashiColors.Text.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(state: VoteConfirmSubmissionState) {
    val isIdle = state.status is VoteSubmissionStatus.Idle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column {
            DetailRow("Poll", state.roundTitle.getValue())
            HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
            DetailRow("Voting power", state.votingWeightZEC.getValue())
            HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
            DetailRow("Voting hotkey", state.hotkeyAddress.getValue(), compactValue = true)
            if (isIdle) {
                HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
                MemoRow(state.memo.getValue())
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    compactValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
            modifier = Modifier.weight(0.95f)
        )
        Text(
            text = if (compactValue) value.toCompactHotkeyLabel() else value,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.35f)
        )
    }
}

@Composable
private fun MemoRow(memo: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Memo", style = ZashiTypography.textSm, color = ZashiColors.Text.textSecondary)
        VerticalSpacer(4.dp)
        Text(
            memo,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BottomSection(state: VoteConfirmSubmissionState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
            .padding(bottom = ZashiDimensions.Spacing.spacingMd)
    ) {
        when (val status = state.status) {
            is VoteSubmissionStatus.Authorizing -> ProgressCard("Authorizing...", status.progress)
            is VoteSubmissionStatus.Submitting ->
                ProgressCard("Submitting vote ${status.current} of ${status.total}...", status.progress)

            else -> Unit
        }
        if (state.status is VoteSubmissionStatus.Authorizing ||
            state.status is VoteSubmissionStatus.Submitting
        ) {
            VerticalSpacer(8.dp)
        }
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.ctaButton
        )
    }
}

@Composable
private fun ProgressCard(
    title: String,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "submission_progress"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
    ) {
        Column(modifier = Modifier.padding(ZashiDimensions.Spacing.spacingXl)) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            VerticalSpacer(12.dp)
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = ZashiColors.Text.textPrimary,
                trackColor = ZashiColors.Surfaces.bgTertiary,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

private fun String.toCompactHotkeyLabel(): String {
    if (isBlank() || equals("Preparing...", ignoreCase = true)) {
        return this
    }

    val trimmed = trim()
    if (trimmed.length <= 18) {
        return trimmed
    }

    return "${trimmed.take(6)}...${trimmed.takeLast(6)}"
}

private fun previewState(status: VoteSubmissionStatus) = VoteConfirmSubmissionState(
    status = status,
    roundTitle = stringRes("NU7 Sentiment Poll"),
    votingWeightZEC = stringRes("1.2500 ZEC"),
    hotkeyAddress = stringRes("zs1xk9...f7q2m"),
    isKeystoneUser = false,
    memo = stringRes("I am authorizing this hotkey managed by my wallet to vote on NU7 Sentiment Poll with 1.2500 ZEC."),
    ctaButton = ButtonState(
        text = stringRes("Confirm"),
        style = ButtonStyle.PRIMARY,
        onClick = {}
    ),
    onBack = {},
)

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewIdle() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Idle)) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewAuthorizing() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Authorizing(0.45f))) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewSubmitting() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Submitting(5, 11, 0.45f))) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewCompleted() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Completed)) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewFailed() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Failed("Network error. Please try again."))) }
