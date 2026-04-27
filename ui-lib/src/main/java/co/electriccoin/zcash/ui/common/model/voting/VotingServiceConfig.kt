package co.electriccoin.zcash.ui.common.model.voting

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class VotingServiceConfig(
    @SerialName("config_version")
    val configVersion: Int = 1,
    @SerialName("vote_round_id")
    val voteRoundId: String = "",
    @SerialName("vote_servers")
    val voteServers: List<ServiceEndpoint> = emptyList(),
    @SerialName("pir_endpoints")
    val pirEndpoints: List<ServiceEndpoint> = emptyList(),
    @SerialName("snapshot_height")
    val snapshotHeight: Long = 0,
    @SerialName("vote_end_time")
    val voteEndTime: Long = 0,
    val proposals: List<Proposal> = emptyList(),
    @SerialName("supported_versions")
    val supportedVersions: SupportedVersions = SupportedVersions(),
) {
    @Serializable
    data class ServiceEndpoint(
        val url: String,
        val label: String
    )

    @Serializable
    data class SupportedVersions(
        val pir: List<String> = emptyList(),
        @SerialName("vote_protocol")
        val voteProtocol: String = "",
        val tally: String = "",
        @SerialName("vote_server")
        val voteServer: String = "",
    )

    fun validate() {
        if (!WalletCapabilities.voteServer.contains(supportedVersions.voteServer)) {
            throw VotingConfigException(
                "Wallet does not support vote_server version " +
                    "\"${supportedVersions.voteServer}\". Please update the wallet."
            )
        }
        if (!WalletCapabilities.voteProtocol.contains(supportedVersions.voteProtocol)) {
            throw VotingConfigException(
                "Wallet does not support vote_protocol version " +
                    "\"${supportedVersions.voteProtocol}\". Please update the wallet."
            )
        }
        if (!WalletCapabilities.tally.contains(supportedVersions.tally)) {
            throw VotingConfigException(
                "Wallet does not support tally version " +
                    "\"${supportedVersions.tally}\". Please update the wallet."
            )
        }
        if (WalletCapabilities.pir.intersect(supportedVersions.pir.toSet()).isEmpty()) {
            throw VotingConfigException(
                "Wallet does not support pir version " +
                    "\"${supportedVersions.pir.joinToString(separator = ",")}\". Please update the wallet."
            )
        }
    }

    fun validateAgainst(session: VotingSession) {
        val configRoundId = voteRoundId.trim().lowercase().takeIf(String::isNotEmpty) ?: return
        val chainRoundId = session.voteRoundId.toLowerHex()
        if (configRoundId != chainRoundId) {
            throw VotingConfigException(
                "Voting config is for round ${configRoundId.take(16)}... but the active round " +
                    "is ${chainRoundId.take(16)}.... Please update the wallet."
            )
        }

        val expectedHash = computeProposalsHash(proposals)
        if (!expectedHash.contentEquals(session.proposalsHash)) {
            throw VotingConfigException(
                "Voting config proposals don't match the active round. Please update the wallet."
            )
        }
    }

    fun encode(): String = votingConfigJson.encodeToString(this)

    companion object {
        val EMPTY = VotingServiceConfig()

        fun decode(raw: String): VotingServiceConfig =
            runCatching {
                votingConfigJson.decodeFromString<VotingServiceConfig>(raw)
            }.getOrElse { throwable ->
                val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
                throw VotingConfigException("Voting config decode failed: $detail")
            }

        fun computeProposalsHash(proposals: List<Proposal>): ByteArray =
            MessageDigest.getInstance("SHA-256")
                .digest(canonicalProposalsJson(proposals).toByteArray(Charsets.UTF_8))

        private fun canonicalProposalsJson(proposals: List<Proposal>): String =
            buildString {
                append('[')
                proposals
                    .sortedBy(Proposal::id)
                    .forEachIndexed { proposalIndex, proposal ->
                        if (proposalIndex > 0) {
                            append(',')
                        }
                        append("{\"id\":")
                        append(proposal.id)
                        append(",\"title\":")
                        append(jsonEncodedString(proposal.title))
                        append(",\"description\":")
                        append(jsonEncodedString(proposal.description))
                        append(",\"options\":[")
                        proposal.options
                            .sortedBy(VoteOption::id)
                            .forEachIndexed { optionIndex, option ->
                                if (optionIndex > 0) {
                                    append(',')
                                }
                                append("{\"index\":")
                                append(option.id)
                                append(",\"label\":")
                                append(jsonEncodedString(option.label))
                                append('}')
                            }
                        append("]}")
                    }
                append(']')
            }
    }
}

class VotingConfigException(message: String) : IllegalStateException(message)

private object WalletCapabilities {
    val voteServer = setOf("v1")
    val voteProtocol = setOf("v0")
    val tally = setOf("v0")
    val pir = setOf("v0")
}

private val votingConfigJson = Json {
    ignoreUnknownKeys = true
}

private fun jsonEncodedString(value: String): String =
    votingConfigJson.encodeToString(value)

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
