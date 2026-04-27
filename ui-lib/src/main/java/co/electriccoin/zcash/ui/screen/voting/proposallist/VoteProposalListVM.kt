package co.electriccoin.zcash.ui.screen.voting.proposallist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class VoteProposalListVM(
    private val args: VoteProposalListArgs,
    votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val prepareVotingRound: PrepareVotingRoundUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    init {
        if (args.roundId.isNotEmpty()) {
            votingSessionStore.selectRound(args.roundId)
            viewModelScope.launch {
                runCatching {
                    prepareVotingRound(args.roundId)
                }.onFailure { throwable ->
                    Log.e("VoteProposalList", "Failed to prepare voting round ${args.roundId}", throwable)
                }
            }
        }
    }

    val state: StateFlow<LceState<VoteProposalListState>> =
        combine(
            votingApiRepository.snapshot,
            votingSessionStore.state,
        ) { apiSnapshot, sessionState ->
            resolveRound(apiSnapshot.rounds, sessionState.selectedRoundId)
                ?.let { round -> createState(round, sessionState.draftVotes) }
        }.map { content ->
            LceState(
                content = content,
                isLoading = content == null
            )
        }.stateIn(
            viewModel = this,
            initialValue = LceState(content = null, isLoading = true)
        )

    private fun resolveRound(
        rounds: List<VotingRound>,
        selectedRoundId: String?
    ): VotingRound? {
        val targetRoundId = args.roundId.ifEmpty { selectedRoundId.orEmpty() }

        return when {
            targetRoundId.isNotEmpty() -> rounds.firstOrNull { it.id == targetRoundId }
            else -> rounds.firstOrNull { it.status == SessionStatus.ACTIVE }
        }
    }

    private fun createState(
        round: VotingRound,
        drafts: Map<Int, Int>
    ): VoteProposalListState {
        val mode = if (args.isReviewMode) VoteProposalListMode.REVIEW else VoteProposalListMode.VOTING
        val proposals = round.proposals
        val votedCount = proposals.count { drafts.containsKey(it.id) }

        return VoteProposalListState(
            mode = mode,
            roundTitle = stringRes(round.title),
            snapshotHeight = round.snapshotHeight.takeIf { it > 0 },
            votedCount = votedCount,
            totalCount = proposals.size,
            metaLine = if (mode == VoteProposalListMode.VOTING) buildMetaLine(round) else null,
            description = round.description.takeIf { it.isNotEmpty() }?.let(::stringRes),
            discussionUrl = round.discussionUrl,
            proposals = proposals.map { buildProposalRow(it, drafts) },
            ctaButton = buildCtaButton(mode, proposals, drafts, round.id),
            onBack = ::onBack,
        )
    }

    private fun buildProposalRow(
        proposal: Proposal,
        drafts: Map<Int, Int>
    ): VoteProposalRowState {
        val draftOptionId = drafts[proposal.id]
        val badge = draftOptionId?.let { buildVoteBadge(proposal, it) }

        return VoteProposalRowState(
            id = proposal.id,
            zipNumber = proposal.zipNumber?.let(::stringRes),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            voteBadge = badge,
            onClick = { onProposalTapped(proposal.id) },
        )
    }

    private fun buildVoteBadge(
        proposal: Proposal,
        optionId: Int
    ): VoteVoteBadgeState {
        val option = proposal.options.firstOrNull { it.id == optionId }
        val label = option?.label ?: "Abstain"
        val type = when {
            label.contains("support", ignoreCase = true) -> VoteVoteBadgeType.SUPPORT
            label.contains("oppose", ignoreCase = true) -> VoteVoteBadgeType.OPPOSE
            else -> VoteVoteBadgeType.ABSTAIN
        }

        return VoteVoteBadgeState(
            label = stringRes(label),
            type = type
        )
    }

    private fun buildCtaButton(
        mode: VoteProposalListMode,
        proposals: List<Proposal>,
        drafts: Map<Int, Int>,
        roundId: String,
    ): ButtonState? {
        if (proposals.isEmpty()) {
            return null
        }

        if (mode == VoteProposalListMode.REVIEW) {
            val allDrafted = proposals.all { drafts.containsKey(it.id) }
            return if (allDrafted) {
                ButtonState(
                    text = stringRes("Submit Votes"),
                    style = ButtonStyle.PRIMARY,
                    onClick = {
                        navigationRouter.forward(
                            VoteConfirmSubmissionArgs(
                                roundIdHex = roundId,
                                choicesJson = drafts.toChoicesJson()
                            )
                        )
                    }
                )
            } else {
                null
            }
        }

        val draftCount = proposals.count { drafts.containsKey(it.id) }
        val firstUnanswered = proposals.firstOrNull { !drafts.containsKey(it.id) }

        return when {
            draftCount == 0 -> ButtonState(
                text = stringRes("Start Voting"),
                style = ButtonStyle.PRIMARY,
                onClick = { onProposalTapped(proposals.first().id) }
            )

            draftCount < proposals.size -> ButtonState(
                text = stringRes("Continue Voting"),
                style = ButtonStyle.PRIMARY,
                onClick = { firstUnanswered?.let { onProposalTapped(it.id) } }
            )

            else -> ButtonState(
                text = stringRes("Review Answers"),
                style = ButtonStyle.PRIMARY,
                onClick = {
                    navigationRouter.forward(
                        VoteProposalListArgs(
                            roundId = roundId,
                            isReviewMode = true
                        )
                    )
                }
            )
        }
    }

    private fun buildMetaLine(round: VotingRound): StringResource {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        val now = Instant.now()
        val remaining = ChronoUnit.SECONDS.between(now, round.votingEnd)
        val dateStr = "Ends ${formatter.format(round.votingEnd)}"
        val timeLeft = when {
            remaining <= 0 -> "Ended"
            remaining < 3600 -> "${remaining / 60}m left"
            remaining < 86400 -> "${remaining / 3600}h left"
            else -> "${remaining / 86400} day${if (remaining / 86400 == 1L) "" else "s"} left"
        }

        return stringRes("$dateStr  ·  $timeLeft")
    }

    private fun onProposalTapped(proposalId: Int) {
        val roundId = args.roundId.ifEmpty {
            votingSessionStore.state.value.selectedRoundId.orEmpty()
        }
        if (roundId.isEmpty()) return

        navigationRouter.forward(
            VoteProposalDetailArgs(
                proposalId = proposalId,
                roundId = roundId,
                isEditingFromReview = args.isReviewMode,
            )
        )
    }

    private fun onBack() = navigationRouter.back()
}

private fun Map<Int, Int>.toChoicesJson(): String =
    JSONObject(toSortedMap().mapKeys { (proposalId, _) -> proposalId.toString() }).toString()
