package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.ChainActiveRoundResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundsResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainTxResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundDto
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface VotingApiProvider {
    suspend fun fetchServiceConfig(): VotingServiceConfig

    suspend fun fetchActiveVotingSession(): VotingSession?

    suspend fun fetchAllRounds(): List<VotingRound>

    suspend fun submitDelegation(registration: DelegationRegistration)

    suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    )

    suspend fun delegateShares(
        shares: List<SharePayload>,
        roundIdHex: String
    )

    suspend fun fetchTxConfirmation(txHash: String): Boolean
}

class KtorVotingApiProvider(
    private val httpClientProvider: HttpClientProvider,
    private val configurationRepository: ConfigurationRepository,
) : VotingApiProvider {
    private var cachedConfig: VotingServiceConfig? = null

    override suspend fun fetchServiceConfig(): VotingServiceConfig =
        resolveConfig().also { cachedConfig = it }

    override suspend fun fetchActiveVotingSession(): VotingSession? =
        execute {
            val baseUrl = resolveBaseUrl() ?: return@execute null
            try {
                val response = get("$baseUrl/shielded-vote/v1/rounds/active").body<ChainActiveRoundResponse>()
                response.round?.toVotingSession()
            } catch (responseException: ResponseException) {
                if (responseException.response.status == HttpStatusCode.NotFound) {
                    null
                } else {
                    throw responseException
                }
            }
        }

    override suspend fun fetchAllRounds(): List<VotingRound> =
        execute {
            val baseUrl = resolveBaseUrl() ?: return@execute emptyList()
            val response = get("$baseUrl/shielded-vote/v1/rounds").body<ChainRoundsResponse>()
            response.rounds?.map(ChainRoundDto::toVotingRound) ?: emptyList()
        }

    override suspend fun submitDelegation(registration: DelegationRegistration) =
        execute {
            val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
            post("$baseUrl/shielded-vote/v1/delegate-vote") {
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
            Unit
        }

    override suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ) = execute {
        val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
        post("$baseUrl/shielded-vote/v1/cast-vote") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("bundle" to bundle, "signature" to signature))
        }
        Unit
    }

    override suspend fun delegateShares(
        shares: List<SharePayload>,
        roundIdHex: String
    ) = execute {
        val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
        post("$baseUrl/shielded-vote/v1/round/$roundIdHex/shares") {
            contentType(ContentType.Application.Json)
            setBody(shares)
        }
        Unit
    }

    override suspend fun fetchTxConfirmation(txHash: String): Boolean =
        execute {
            val baseUrl = resolveBaseUrl() ?: return@execute false
            runCatching {
                get("$baseUrl/shielded-vote/v1/tx/$txHash").body<ChainTxResponse>().tx?.confirmed ?: false
            }.getOrDefault(false)
        }

    private suspend fun resolveConfig(): VotingServiceConfig {
        val configuration = configurationRepository.configurationFlow.value
        val configUrl = configuration?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue).orEmpty()
        val serverUrl = configuration?.let(ConfigurationEntries.VOTING_SERVER_URL::getValue).orEmpty()

        if (configUrl.isNotEmpty()) {
            val remoteConfig = runCatching {
                execute {
                    get(configUrl).body<VotingServiceConfig>()
                }
            }.getOrNull()

            if (remoteConfig != null) {
                return remoteConfig
            }
        }

        if (serverUrl.isNotEmpty()) {
            return VotingServiceConfig(
                version = 1,
                voteServers = listOf(
                    VotingServiceConfig.ServiceEndpoint(
                        url = serverUrl.trimEnd('/'),
                        label = "configured"
                    )
                )
            )
        }

        return VotingServiceConfig.EMPTY
    }

    private suspend fun resolveBaseUrl(): String? {
        val config = cachedConfig ?: fetchServiceConfig()
        return config.voteServers.firstOrNull()?.url?.trimEnd('/')
    }

    private suspend inline fun <T> execute(
        crossinline block: suspend HttpClient.() -> T
    ): T =
        withContext(Dispatchers.IO) {
            httpClientProvider.create().use { block(it) }
        }
}
