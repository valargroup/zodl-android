package co.electriccoin.zcash.ui.screen.voting.results

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

data class VoteResultsState(
    val roundTitle: StringResource,
    val roundDescription: StringResource,
    val proposals: List<VoteProposalResultState>,
    val isLoadingResults: Boolean,
    val doneButton: ButtonState,
    val onBack: () -> Unit,
)

data class VoteProposalResultState(
    val zipNumber: StringResource?,
    val title: StringResource,
    val description: StringResource,
    val options: List<VoteOptionResultState>,
    val totalZec: StringResource,
    val winnerLabel: StringResource?,
    val winnerColor: VoteOptionColor,
)

data class VoteOptionResultState(
    val label: StringResource,
    val amountZec: StringResource,
    val fraction: Float,
    val color: VoteOptionColor,
    val isWinner: Boolean,
)

enum class VoteOptionColor {
    SUPPORT,
    OPPOSE,
    ABSTAIN,
    OTHER,
}
