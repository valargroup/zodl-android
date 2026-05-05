package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.abstainOptionId
import java.time.Instant

// After submission, synthetic abstains exist only in draft/recovery state, not on-wire.
fun VotingRecoverySnapshot.effectiveChoices(
    proposals: List<Proposal>,
    inMemoryDraftChoices: Map<Int, Int> = emptyMap(),
): Map<Int, Int> =
    buildMap {
        proposals.forEach { proposal ->
            val choiceId = inMemoryDraftChoices[proposal.id]
                ?: draftChoices[proposal.id]
                ?: proposalSelections[proposal.id]?.choiceId
                ?: proposal.abstainOptionId().takeIf { submittedAtEpochSeconds != null }

            if (choiceId != null) {
                put(proposal.id, choiceId)
            }
        }
    }

internal fun VotingRecoverySnapshot.withProposalSubmitted(
    proposalId: Int,
    updatedAt: Instant = Instant.now()
): VotingRecoverySnapshot =
    copy(
        draftChoices = draftChoices - proposalId,
        submittedProposalIds = submittedProposalIds + proposalId,
        updatedAt = updatedAt
    )
