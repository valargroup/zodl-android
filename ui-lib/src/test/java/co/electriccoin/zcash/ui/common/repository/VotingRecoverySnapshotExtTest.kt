package co.electriccoin.zcash.ui.common.repository

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VotingRecoverySnapshotExtTest {
    @Test
    fun submittedProposalIsRemovedFromOutstandingDrafts() {
        val selection = VotingProposalSelection(choiceId = 10, numOptions = 3)
        val updatedAt = Instant.parse("2026-05-05T12:00:00Z")
        val snapshot = VotingRecoverySnapshot(
            accountUuid = "account",
            roundId = "round",
            draftChoices = mapOf(
                1 to 10,
                2 to 20
            ),
            proposalSelections = mapOf(
                1 to selection,
                2 to VotingProposalSelection(choiceId = 20, numOptions = 3)
            ),
            submittedProposalIds = setOf(3)
        )

        val submitted = snapshot.withProposalSubmitted(
            proposalId = 1,
            updatedAt = updatedAt
        )

        assertEquals(mapOf(2 to 20), submitted.draftChoices)
        assertEquals(setOf(1, 3), submitted.submittedProposalIds)
        assertEquals(selection, submitted.proposalSelections[1])
        assertEquals(updatedAt, submitted.updatedAt)
    }
}
