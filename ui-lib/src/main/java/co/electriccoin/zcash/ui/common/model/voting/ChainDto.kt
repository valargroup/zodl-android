@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.time.Instant
import java.util.Base64

@Serializable
@JsonIgnoreUnknownKeys
data class ChainRoundsResponse(
    val rounds: List<ChainRoundDto>? = null
)

@Serializable
@JsonIgnoreUnknownKeys
data class ChainActiveRoundResponse(
    val round: ChainRoundDto? = null
)

@Serializable
data class ChainTxResponse(
    val tx: ChainTxDto? = null
)

@Serializable
data class ChainTxDto(
    val hash: String = "",
    val confirmed: Boolean = false,
)

@Serializable
@JsonIgnoreUnknownKeys
data class ChainRoundDto(
    @SerialName("vote_round_id") val voteRoundId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("snapshot_height") val snapshotHeight: Long,
    @SerialName("snapshot_blockhash") val snapshotBlockhash: String = "",
    @SerialName("vote_end_time") val voteEndTime: Long,
    @SerialName("ceremony_phase_start") val ceremonyPhaseStart: Long = 0,
    @SerialName("status") val status: Int = 1,
    @SerialName("proposals") val proposals: List<ChainProposalDto> = emptyList(),
    @SerialName("proposals_hash") val proposalsHash: String = "",
    @SerialName("ea_pk") val eaPk: String = "",
    @SerialName("vk_zkp1") val vkZkp1: String = "",
    @SerialName("vk_zkp2") val vkZkp2: String = "",
    @SerialName("vk_zkp3") val vkZkp3: String = "",
    @SerialName("nc_root") val ncRoot: String = "",
    @SerialName("nullifier_imt_root") val nullifierImtRoot: String = "",
    @SerialName("creator") val creator: String = "",
    @SerialName("discussion_url") val discussionUrl: String? = null,
    @SerialName("created_at_height") val createdAtHeight: Long = 0,
) {
    fun toVotingRound(): VotingRound =
        VotingRound(
            id = voteRoundId.normalizeRoundId(),
            title = title,
            description = description,
            discussionUrl = discussionUrl,
            snapshotHeight = snapshotHeight,
            snapshotDate = Instant.ofEpochSecond(ceremonyPhaseStart.takeIf { it > 0 } ?: voteEndTime),
            votingStart = Instant.ofEpochSecond(ceremonyPhaseStart),
            votingEnd = Instant.ofEpochSecond(voteEndTime),
            proposals = proposals.map { it.toProposal() },
            status = when (status) {
                1 -> SessionStatus.ACTIVE
                2 -> SessionStatus.TALLYING
                3 -> SessionStatus.COMPLETED
                else -> SessionStatus.CANCELLED
            }
        )

    fun toVotingSession(): VotingSession =
        VotingSession(
            voteRoundId = voteRoundId.decodeBinaryField(),
            snapshotHeight = snapshotHeight,
            snapshotBlockhash = snapshotBlockhash.decodeBinaryField(),
            proposalsHash = proposalsHash.decodeBinaryField(),
            voteEndTime = Instant.ofEpochSecond(voteEndTime),
            ceremonyStart = Instant.ofEpochSecond(ceremonyPhaseStart),
            eaPK = eaPk.decodeBinaryField(),
            vkZkp1 = vkZkp1.decodeBinaryField(),
            vkZkp2 = vkZkp2.decodeBinaryField(),
            vkZkp3 = vkZkp3.decodeBinaryField(),
            ncRoot = ncRoot.decodeBinaryField(),
            nullifierIMTRoot = nullifierImtRoot.decodeBinaryField(),
            creator = creator,
            title = title,
            description = description,
            discussionUrl = discussionUrl,
            proposals = proposals.map { it.toProposal() },
            status = when (status) {
                1 -> SessionStatus.ACTIVE
                2 -> SessionStatus.TALLYING
                3 -> SessionStatus.COMPLETED
                else -> SessionStatus.CANCELLED
            },
            createdAtHeight = createdAtHeight
        )
}

@Serializable
@JsonIgnoreUnknownKeys
data class ChainProposalDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("options") val options: List<ChainVoteOptionDto> = emptyList(),
    @SerialName("zip_number") val zipNumber: String? = null,
    @SerialName("forum_url") val forumUrl: String? = null,
) {
    fun toProposal(): Proposal =
        Proposal(
            id = id,
            title = title,
            description = description,
            options = options.mapIndexed { index, option -> option.toVoteOption(index) },
            zipNumber = zipNumber,
            forumUrl = forumUrl
        )
}

@Serializable
@JsonIgnoreUnknownKeys
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

private fun String.normalizeRoundId(): String {
    val trimmed = trim()
    if (trimmed.isHexEncoded()) {
        return trimmed.lowercase()
    }

    val decoded = runCatching { Base64.getDecoder().decode(trimmed) }.getOrNull()
    return decoded?.toHexString() ?: trimmed
}

private fun String.decodeBinaryField(): ByteArray {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ByteArray(0)
    if (trimmed.isHexEncoded()) return trimmed.hexToBytes()

    return runCatching { Base64.getDecoder().decode(trimmed) }.getOrDefault(ByteArray(0))
}

private fun String.isHexEncoded(): Boolean =
    isNotEmpty() && length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { chunk -> chunk.toInt(16).toByte() }.toByteArray()

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }
