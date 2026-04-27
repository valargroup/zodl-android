package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteCoinholderPollingVM(
    votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteCoinholderPollingState>> =
        combine(
            votingApiRepository.snapshot,
            votingSessionStore.state
        ) { apiSnapshot, sessionState ->
            val (activeSrc, pastSrc) = apiSnapshot.rounds
                .reversed()
                .partition { it.status == SessionStatus.ACTIVE }

            VoteCoinholderPollingState(
                activeRounds = activeSrc.map { buildCard(it, sessionState.submittedRounds[it.id]) },
                pastRounds = pastSrc.map { buildCard(it, sessionState.submittedRounds[it.id]) },
                onBack = ::onBack
            )
        }.map { content ->
            LceState(content = content, isLoading = false)
        }.stateIn(this)

    private fun buildCard(
        round: VotingRound,
        votedProposalCount: Int?,
    ): VotePollCardState {
        val status = when {
            votedProposalCount != null -> VotePollCardStatus.VOTED
            round.status == SessionStatus.ACTIVE -> VotePollCardStatus.ACTIVE
            else -> VotePollCardStatus.CLOSED
        }

        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val dateLabel = when (status) {
            VotePollCardStatus.ACTIVE -> "Closes ${formatter.format(round.votingEnd)}"
            VotePollCardStatus.VOTED -> "Closes ${formatter.format(round.votingEnd)}"
            VotePollCardStatus.CLOSED -> "Closed ${formatter.format(round.votingEnd)}"
        }
        val count = votedProposalCount ?: 0
        val total = round.proposals.size

        return VotePollCardState(
            roundId = round.id,
            title = stringRes(round.title),
            description = if (round.description.isNotEmpty()) {
                stringRes(round.description)
            } else {
                stringRes("")
            },
            status = status,
            isActionEnabled = status == VotePollCardStatus.ACTIVE,
            dateLabel = stringRes(dateLabel),
            votedLabel = if (votedProposalCount != null) {
                stringRes("$count of $total voted")
            } else {
                null
            },
            proposalCount = total,
            votedCount = count,
            onAction = { onRoundSelected(round, status) }
        )
    }

    private fun onRoundSelected(
        round: VotingRound,
        status: VotePollCardStatus
    ) {
        if (status != VotePollCardStatus.ACTIVE) return

        votingSessionStore.selectRound(round.id)
        navigationRouter.forward(VoteProposalListArgs(roundId = round.id))
    }

    private fun onBack() = navigationRouter.back()
}
