package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.Serializable

@Serializable
data class VotingServiceConfig(
    val version: Int,
    val voteServers: List<ServiceEndpoint>,
    val pirServers: List<ServiceEndpoint> = emptyList(),
) {
    @Serializable
    data class ServiceEndpoint(
        val url: String,
        val label: String
    )

    companion object {
        val EMPTY = VotingServiceConfig(
            version = 1,
            voteServers = emptyList(),
            pirServers = emptyList(),
        )
    }
}
