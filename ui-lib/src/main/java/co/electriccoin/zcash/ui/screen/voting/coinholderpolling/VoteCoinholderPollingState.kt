package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteCoinholderPollingState(
    val activeRounds: List<VotePollCardState>,
    val pastRounds: List<VotePollCardState>,
    val refreshError: StringResource? = null,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

enum class VotePollCardStatus {
    ACTIVE,
    VOTED,
    CLOSED
}

@Immutable
data class VotePollCardState(
    val roundId: String,
    val title: StringResource,
    val description: StringResource,
    val status: VotePollCardStatus,
    val isActionEnabled: Boolean,
    val dateLabel: StringResource,
    val votedLabel: StringResource?,
    val proposalCount: Int,
    val votedCount: Int,
    val onAction: () -> Unit,
)
