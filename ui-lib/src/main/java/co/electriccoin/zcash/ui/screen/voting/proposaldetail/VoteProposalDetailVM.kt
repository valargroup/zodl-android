package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class VoteProposalDetailVM(
    private val args: VoteProposalDetailArgs,
    votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val showUnansweredSheet = MutableStateFlow(false)

    init {
        votingSessionStore.selectRound(args.roundId)
    }

    val state: StateFlow<LceState<VoteProposalDetailState>> =
        combine(
            votingApiRepository.snapshot,
            votingSessionStore.state,
            showUnansweredSheet,
        ) { apiSnapshot, sessionState, showSheet ->
            apiSnapshot.rounds
                .firstOrNull { it.id == args.roundId }
                ?.let { round -> createState(round, sessionState.draftVotes, showSheet) }
        }.map { content ->
            LceState(
                content = content,
                isLoading = content == null
            )
        }.stateIn(
            viewModel = this,
            initialValue = LceState(content = null, isLoading = true)
        )

    private fun createState(
        round: VotingRound,
        drafts: Map<Int, Int>,
        showSheet: Boolean,
    ): VoteProposalDetailState {
        val proposals = round.proposals
        val requestedIndex = proposals.indexOfFirst { it.id == args.proposalId }
        val proposalIndex = requestedIndex.takeIf { it >= 0 } ?: 0
        val proposal = proposals.getOrElse(proposalIndex) { proposals.first() }
        val position = proposalIndex + 1
        val selectedOptionId = drafts[proposal.id]
        val unansweredCount = proposals.count { !drafts.containsKey(it.id) }

        return VoteProposalDetailState(
            positionLabel = stringRes("$position OF ${proposals.size}"),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            forumUrl = proposal.forumUrl,
            options = buildOptions(proposal, selectedOptionId, args.isReadOnly),
            isLocked = args.isReadOnly,
            isEditingFromReview = args.isEditingFromReview,
            showUnansweredSheet = showSheet,
            unansweredCount = unansweredCount,
            onBack = ::onBack,
            onNext = { onNext(proposals, proposalIndex, drafts) },
            onConfirmUnanswered = { onConfirmUnanswered(round) },
            onDismissUnanswered = { showUnansweredSheet.value = false },
        )
    }

    private fun buildOptions(
        proposal: Proposal,
        selectedOptionId: Int?,
        isReadOnly: Boolean
    ): List<VoteVoteOptionRowState> {
        val options = proposal.options.toMutableList()
        if (options.none { it.label.contains("abstain", ignoreCase = true) }) {
            val nextIndex = (options.maxOfOrNull(VoteOption::id) ?: 0) + 1
            options += VoteOption(id = nextIndex, label = "Abstain")
        }

        val total = options.size

        return options.mapIndexed { index, option ->
            VoteVoteOptionRowState(
                index = option.id,
                label = stringRes(option.label),
                color = option.toVoteVoteOptionColor(total, index),
                isSelected = selectedOptionId == option.id,
                isLocked = isReadOnly,
                onSelect = { votingSessionStore.toggleDraftVote(proposal.id, option.id) },
            )
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun onNext(
        proposals: List<Proposal>,
        currentIndex: Int,
        drafts: Map<Int, Int>
    ) {
        val isLast = currentIndex == proposals.lastIndex
        if (!isLast) {
            val nextProposal = proposals[currentIndex + 1]
            navigationRouter.forward(
                VoteProposalDetailArgs(
                    proposalId = nextProposal.id,
                    roundId = args.roundId,
                    isEditingFromReview = args.isEditingFromReview,
                    isReadOnly = args.isReadOnly,
                )
            )
            return
        }

        val allDrafted = proposals.all { drafts.containsKey(it.id) }
        if (allDrafted) {
            navigationRouter.forward(
                VoteProposalListArgs(
                    roundId = args.roundId,
                    mode = VoteProposalListMode.REVIEW
                )
            )
        } else {
            showUnansweredSheet.value = true
        }
    }

    private fun onConfirmUnanswered(round: VotingRound) {
        showUnansweredSheet.value = false
        votingSessionStore.abstainUnanswered(round.proposals)
        navigationRouter.forward(
            VoteProposalListArgs(
                roundId = round.id,
                mode = VoteProposalListMode.REVIEW
            )
        )
    }
}

private fun VoteOption.toVoteVoteOptionColor(
    total: Int,
    index: Int
): VoteVoteOptionColor {
    if (label.contains("abstain", ignoreCase = true)) {
        return VoteVoteOptionColor.ABSTAIN
    }

    return when (total) {
        1 -> VoteVoteOptionColor.SUPPORT
        2 -> if (index == 0) VoteVoteOptionColor.SUPPORT else VoteVoteOptionColor.OPPOSE
        else -> when (index % 3) {
            0 -> VoteVoteOptionColor.SUPPORT
            1 -> VoteVoteOptionColor.OPPOSE
            else -> VoteVoteOptionColor.OTHER
        }
    }
}
