package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VotingEligibility {
    UNKNOWN,
    WALLET_SYNCING,
    ELIGIBLE,
    INELIGIBLE
}

data class VotingSessionStoreState(
    val selectedRoundId: String? = null,
    val draftVotes: Map<Int, Int> = emptyMap(),
    val submittedRounds: Map<String, Int> = emptyMap(),
    val eligibility: VotingEligibility = VotingEligibility.UNKNOWN
)

interface VotingSessionStore {
    val state: StateFlow<VotingSessionStoreState>

    fun selectRound(roundId: String?)

    fun restoreDraftVotes(
        roundId: String,
        draftVotes: Map<Int, Int>
    )

    fun setEligibility(eligibility: VotingEligibility)

    fun toggleDraftVote(
        proposalId: Int,
        optionId: Int
    )

    fun abstainUnanswered(proposals: List<Proposal>)

    fun markRoundSubmitted(
        roundId: String,
        proposalCount: Int
    )

    fun clearDraftVotes()

    fun clear()
}

class VotingSessionStoreImpl : VotingSessionStore {
    private val mutableState = MutableStateFlow(VotingSessionStoreState())

    override val state: StateFlow<VotingSessionStoreState> = mutableState.asStateFlow()

    override fun selectRound(roundId: String?) {
        mutableState.update { current ->
            if (current.selectedRoundId == roundId) {
                current
            } else {
                current.copy(
                    selectedRoundId = roundId,
                    draftVotes = emptyMap()
                )
            }
        }
    }

    override fun restoreDraftVotes(
        roundId: String,
        draftVotes: Map<Int, Int>
    ) {
        mutableState.update { current ->
            current.copy(
                selectedRoundId = roundId,
                draftVotes = draftVotes.toMap()
            )
        }
    }

    override fun setEligibility(eligibility: VotingEligibility) {
        mutableState.update { current -> current.copy(eligibility = eligibility) }
    }

    override fun toggleDraftVote(
        proposalId: Int,
        optionId: Int
    ) {
        mutableState.update { current ->
            val updatedDrafts = current.draftVotes.toMutableMap()
            if (updatedDrafts[proposalId] == optionId) {
                updatedDrafts.remove(proposalId)
            } else {
                updatedDrafts[proposalId] = optionId
            }

            current.copy(draftVotes = updatedDrafts)
        }
    }

    override fun abstainUnanswered(proposals: List<Proposal>) {
        mutableState.update { current ->
            val updatedDrafts = current.draftVotes.toMutableMap()

            proposals.forEach { proposal ->
                if (updatedDrafts.containsKey(proposal.id)) {
                    return@forEach
                }

                updatedDrafts[proposal.id] = proposal.abstainOptionId()
            }

            current.copy(draftVotes = updatedDrafts)
        }
    }

    override fun markRoundSubmitted(
        roundId: String,
        proposalCount: Int
    ) {
        mutableState.update { current ->
            current.copy(
                submittedRounds = current.submittedRounds + (roundId to proposalCount)
            )
        }
    }

    override fun clearDraftVotes() {
        mutableState.update { current -> current.copy(draftVotes = emptyMap()) }
    }

    override fun clear() {
        mutableState.value = VotingSessionStoreState()
    }
}

private fun Proposal.abstainOptionId(): Int =
    options
        .firstOrNull { option -> option.label.contains("abstain", ignoreCase = true) }
        ?.id
        ?: ((options.maxOfOrNull(VoteOption::id) ?: 0) + 1)
