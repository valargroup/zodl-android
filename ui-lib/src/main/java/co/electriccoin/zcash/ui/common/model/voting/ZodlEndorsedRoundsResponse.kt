package co.electriccoin.zcash.ui.common.model.voting

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZodlEndorsedRoundsResponse(
    @SerialName("vote_round_ids")
    val voteRoundIds: List<String> = emptyList()
) {
    fun roundIdsHex(): Set<String> =
        voteRoundIds.mapNotNull { encoded ->
            runCatching { Base64.getDecoder().decode(encoded) }
                .getOrNull()
                ?.takeIf { bytes -> bytes.size == ROUND_ID_BYTES }
                ?.toLowerHex()
        }.toSet()

    private companion object {
        const val ROUND_ID_BYTES = 32
    }
}
