package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.displayColor
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.optionsWithAbstain
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class VoteProposalDetailVM(
    private val args: VoteProposalDetailArgs,
    votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
) : ViewModel() {
    private val showUnansweredSheet = MutableStateFlow(false)
    private val selectedAccountUuid: Flow<String> =
        observeSelectedWalletAccount.require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }

    val state: StateFlow<LceState<VoteProposalDetailState>> =
        combine(
            votingApiRepository.snapshot,
            votingSessionStore.state,
            selectedAccountUuid,
            showUnansweredSheet,
        ) { apiSnapshot, sessionState, accountUuid, showSheet ->
            apiSnapshot.rounds
                .firstOrNull { it.id == args.roundId }
                ?.let { round ->
                    createState(
                        round = round,
                        drafts = sessionState.draftVotesFor(accountUuid, args.roundId),
                        accountUuid = accountUuid,
                        showSheet = showSheet
                    )
                }
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
        accountUuid: String,
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
            options = buildOptions(proposal, selectedOptionId, accountUuid, args.isReadOnly),
            isLocked = args.isReadOnly,
            isEditingFromReview = args.isEditingFromReview,
            showUnansweredSheet = showSheet,
            unansweredCount = unansweredCount,
            onBack = ::onBack,
            onNext = { onNext(proposals, proposalIndex, drafts) },
            onConfirmUnanswered = { onConfirmUnanswered(accountUuid, round) },
            onDismissUnanswered = { showUnansweredSheet.value = false },
        )
    }

    private fun buildOptions(
        proposal: Proposal,
        selectedOptionId: Int?,
        accountUuid: String,
        isReadOnly: Boolean
    ): List<VoteVoteOptionRowState> {
        val options = proposal.optionsWithAbstain()
        val total = options.size

        return options.mapIndexed { index, option ->
            VoteVoteOptionRowState(
                index = option.id,
                label = stringRes(option.label),
                color = option.displayColor(position = index, total = total),
                isSelected = selectedOptionId == option.id,
                isLocked = isReadOnly,
                onSelect = {
                    votingSessionStore.toggleDraftVote(
                        accountUuid = accountUuid,
                        roundId = args.roundId,
                        proposalId = proposal.id,
                        optionId = option.id
                    )
                },
            )
        }
    }

    private fun onBack() = navigationRouter.backTo(VoteProposalListArgs::class)

    private fun onNext(
        proposals: List<Proposal>,
        currentIndex: Int,
        drafts: Map<Int, Int>
    ) {
        val isLast = currentIndex == proposals.lastIndex
        if (!isLast) {
            val nextProposal = proposals[currentIndex + 1]
            navigationRouter.replace(
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
            navigationRouter.replace(
                VoteProposalListArgs(
                    roundId = args.roundId,
                    mode = VoteProposalListMode.REVIEW
                )
            )
        } else {
            showUnansweredSheet.value = true
        }
    }

    private fun onConfirmUnanswered(
        accountUuid: String,
        round: VotingRound
    ) {
        showUnansweredSheet.value = false
        votingSessionStore.abstainUnanswered(
            accountUuid = accountUuid,
            roundId = round.id,
            proposals = round.proposals
        )
        navigationRouter.replace(
            VoteProposalListArgs(
                roundId = round.id,
                mode = VoteProposalListMode.REVIEW
            )
        )
    }
}
