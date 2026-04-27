package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ChainRoundsResponse(
    val rounds: List<ChainRoundDto>? = null
)

@Serializable
data class ChainRoundDto(
    @SerialName("vote_round_id") val voteRoundId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("snapshot_height") val snapshotHeight: Long,
    @SerialName("vote_end_time") val voteEndTime: Long,
    @SerialName("status") val status: Int = 1,
    @SerialName("proposals") val proposals: List<ChainProposalDto> = emptyList(),
) {
    fun toVotingRound(): VotingRound =
        VotingRound(
            id = voteRoundId,
            title = title,
            description = description,
            discussionUrl = null,
            snapshotHeight = snapshotHeight,
            snapshotDate = Instant.ofEpochSecond(voteEndTime),
            votingStart = Instant.EPOCH,
            votingEnd = Instant.ofEpochSecond(voteEndTime),
            proposals = proposals.map { it.toProposal() },
            status = when (status) {
                1 -> SessionStatus.ACTIVE
                2 -> SessionStatus.TALLYING
                3 -> SessionStatus.COMPLETED
                else -> SessionStatus.CANCELLED
            }
        )
}

@Serializable
data class ChainProposalDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("options") val options: List<ChainVoteOptionDto> = emptyList(),
) {
    fun toProposal(): Proposal =
        Proposal(
            id = id,
            title = title,
            description = description,
            options = options.mapIndexed { index, option -> option.toVoteOption(index) },
            zipNumber = null,
            forumUrl = null
        )
}

@Serializable
data class ChainVoteOptionDto(
    @SerialName("label") val label: String,
    @SerialName("index") val index: Int? = null,
) {
    fun toVoteOption(fallbackIndex: Int): VoteOption =
        VoteOption(
            id = index ?: fallbackIndex,
            label = label
        )
}
