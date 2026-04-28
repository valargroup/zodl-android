package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.abstainOptionId

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
