package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.ChainActiveRoundResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundsResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundDto
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.ShareConfirmationResult
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.toBase64String
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface VotingApiProvider {
    suspend fun fetchServiceConfig(): VotingServiceConfig

    suspend fun fetchActiveVotingSession(): VotingSession?

    suspend fun fetchAllRounds(): List<VotingRound>

    suspend fun submitDelegation(registration: DelegationRegistration): TxResult

    suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ): TxResult

    suspend fun delegateShares(
        shares: List<SharePayload>,
        roundIdHex: String
    ): List<DelegatedShareInfo>

    suspend fun fetchShareStatus(
        helperBaseUrl: String,
        roundIdHex: String,
        nullifierHex: String
    ): ShareConfirmationResult

    suspend fun resubmitShare(
        payload: SharePayload,
        roundIdHex: String,
        excludeUrls: List<String>
    ): List<String>

    suspend fun fetchTxConfirmation(txHash: String): TxConfirmation?
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

    override suspend fun submitDelegation(registration: DelegationRegistration): TxResult =
        execute {
            val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
            postTxResult(
                url = "$baseUrl/shielded-vote/v1/delegate-vote",
                body = mapOf(
                    "rk" to registration.rk.toBase64String(),
                    "spend_auth_sig" to registration.spendAuthSig.toBase64String(),
                    "sighash" to registration.sighash.toBase64String(),
                    "signed_note_nullifier" to registration.signedNoteNullifier.toBase64String(),
                    "cmx_new" to registration.cmxNew.toBase64String(),
                    "van_cmx" to registration.vanCmx.toBase64String(),
                    "gov_nullifiers" to registration.govNullifiers.map(ByteArray::toBase64String),
                    "proof" to registration.proof.toBase64String(),
                    "vote_round_id" to registration.voteRoundId.toBase64String()
                )
            )
        }

    override suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ): TxResult = execute {
        val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
        postTxResult(
            url = "$baseUrl/shielded-vote/v1/cast-vote",
            body = mapOf(
                "van_nullifier" to bundle.vanNullifier.toBase64String(),
                "vote_authority_note_new" to bundle.voteAuthorityNoteNew.toBase64String(),
                "vote_commitment" to bundle.voteCommitment.toBase64String(),
                "proposal_id" to bundle.proposalId,
                "proof" to bundle.proof.toBase64String(),
                "vote_round_id" to bundle.voteRoundId.hexToBase64String(),
                "vote_comm_tree_anchor_height" to bundle.anchorHeight,
                "r_vpk" to bundle.rVpkBytes.toBase64String(),
                "vote_auth_sig" to signature.voteAuthSig.toBase64String()
            )
        )
    }

    override suspend fun delegateShares(
        shares: List<SharePayload>,
        roundIdHex: String
    ): List<DelegatedShareInfo> = execute {
        if (shares.isEmpty()) {
            return@execute emptyList()
        }

        val config = cachedConfig ?: fetchServiceConfig()
        val serverUrls = config.voteServers
            .map { endpoint -> endpoint.url.trimEnd('/') }
            .distinct()

        if (serverUrls.isEmpty()) {
            error("Voting server URL is not configured")
        }

        buildList {
            for (share in shares) {
                val acceptedByServers = mutableListOf<String>()
                val body = share.toApiBody(roundIdHex)

                for (serverUrl in serverUrls) {
                    val accepted = runCatching {
                        post("$serverUrl/shielded-vote/v1/shares") {
                            contentType(ContentType.Application.Json)
                            setBody(body)
                        }
                    }.isSuccess

                    if (accepted) {
                        acceptedByServers += serverUrl
                    }
                }

                if (acceptedByServers.isEmpty()) {
                    error("No voting server accepted share ${share.encShare.shareIndex}")
                }

                add(
                    DelegatedShareInfo(
                        shareIndex = share.encShare.shareIndex,
                        proposalId = share.proposalId,
                        acceptedByServers = acceptedByServers
                    )
                )
            }
        }
    }

    override suspend fun fetchShareStatus(
        helperBaseUrl: String,
        roundIdHex: String,
        nullifierHex: String
    ): ShareConfirmationResult = execute {
        val responseJson = get(
            "${helperBaseUrl.trimEnd('/')}/shielded-vote/v1/share-status/$roundIdHex/$nullifierHex"
        ) {
            header("Accept", "application/json")
            header("X-Helper-Token", "voting-helper")
        }.bodyAsText()
        when (JSONObject(responseJson).optString("status")) {
            "confirmed" -> ShareConfirmationResult.CONFIRMED
            else -> ShareConfirmationResult.PENDING
        }
    }

    override suspend fun resubmitShare(
        payload: SharePayload,
        roundIdHex: String,
        excludeUrls: List<String>
    ): List<String> = execute {
        val config = cachedConfig ?: fetchServiceConfig()
        val allServers = config.voteServers
            .map { endpoint -> endpoint.url.trimEnd('/') }
            .distinct()
        val candidateServers = allServers.filterNot(excludeUrls::contains)
            .ifEmpty { allServers }

        buildList {
            for (serverUrl in candidateServers) {
                val accepted = runCatching {
                    post("$serverUrl/shielded-vote/v1/shares") {
                        contentType(ContentType.Application.Json)
                        setBody(payload.withSubmitAt(0).toApiBody(roundIdHex))
                    }
                }.isSuccess

                if (accepted) {
                    add(serverUrl)
                    break
                }
            }
        }
    }

    override suspend fun fetchTxConfirmation(txHash: String): TxConfirmation? =
        execute {
            val baseUrl = resolveBaseUrl() ?: return@execute null
            runCatching {
                get("$baseUrl/shielded-vote/v1/tx/$txHash").bodyAsText().toTxConfirmation()
            }.recoverCatching { throwable ->
                val responseException = throwable as? ResponseException ?: throw throwable
                when (responseException.response.status) {
                    HttpStatusCode.NotFound -> return@execute null
                    HttpStatusCode.UnprocessableEntity -> responseException.response.bodyAsText().toTxConfirmation()
                    else -> throw responseException
                }
            }.getOrThrow()
        }

    private suspend fun resolveConfig(): VotingServiceConfig {
        val configuration = configurationRepository.configurationFlow.value
        val configUrl = configuration?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue).orEmpty()
        val serverUrl = configuration?.let(ConfigurationEntries.VOTING_SERVER_URL::getValue).orEmpty()

        if (configUrl.isNotEmpty()) {
            return execute {
                get(configUrl).bodyAsText()
            }.let(VotingServiceConfig::decode)
                .also(VotingServiceConfig::validate)
        }

        if (serverUrl.isNotEmpty()) {
            return VotingServiceConfig(
                voteServers = listOf(
                    VotingServiceConfig.ServiceEndpoint(
                        url = serverUrl.trimEnd('/'),
                        label = "configured"
                    )
                ),
                supportedVersions = VotingServiceConfig.SupportedVersions(
                    pir = listOf("v0"),
                    voteProtocol = "v0",
                    tally = "v0",
                    voteServer = "v1"
                )
            )
        }

        return VotingServiceConfig.EMPTY
    }

    private suspend fun resolveBaseUrl(): String? {
        val config = cachedConfig ?: fetchServiceConfig()
        return config.voteServers.firstOrNull()?.url?.trimEnd('/')
    }

    private suspend fun HttpClient.postTxResult(
        url: String,
        body: Any
    ): TxResult =
        try {
            post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText().toTxResult()
        } catch (responseException: ResponseException) {
            if (responseException.response.status == HttpStatusCode.UnprocessableEntity) {
                responseException.response.bodyAsText().toTxResult()
            } else {
                throw responseException
            }
        }

    private suspend inline fun <T> execute(
        crossinline block: suspend HttpClient.() -> T
    ): T =
        withContext(Dispatchers.IO) {
            httpClientProvider.create().use { block(it) }
        }
}

private fun SharePayload.toApiBody(roundIdHex: String) =
    mapOf(
        "shares_hash" to sharesHash.toBase64String(),
        "proposal_id" to proposalId,
        "vote_decision" to voteDecision,
        "enc_share" to mapOf(
            "c1" to encShare.c1.toBase64String(),
            "c2" to encShare.c2.toBase64String(),
            "share_index" to encShare.shareIndex
        ),
        "share_index" to encShare.shareIndex,
        "tree_position" to treePosition,
        "vote_round_id" to roundIdHex,
        "all_enc_shares" to allEncShares.map { share ->
            mapOf(
                "c1" to share.c1.toBase64String(),
                "c2" to share.c2.toBase64String(),
                "share_index" to share.shareIndex
            )
        },
        "share_comms" to shareComms.map(ByteArray::toBase64String),
        "primary_blind" to primaryBlind.toBase64String(),
        "submit_at" to submitAt
    )

private fun String.toTxResult(): TxResult {
    val json = JSONObject(this)
    return TxResult(
        txHash = json.optString("tx_hash"),
        code = json.optNumber("code").toInt(),
        log = json.optString("log")
    )
}

private fun String.toTxConfirmation(): TxConfirmation {
    val json = JSONObject(this)
    val events = json.optJSONArray("events")
    return TxConfirmation(
        height = json.optNumber("height").toLong(),
        code = json.optNumber("code").toInt(),
        log = json.optString("log"),
        events = buildList {
            if (events == null) return@buildList
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                val attributes = event.optJSONArray("attributes")
                add(
                    TxEvent(
                        type = event.optString("type"),
                        attributes = buildList {
                            if (attributes == null) return@buildList
                            for (attributeIndex in 0 until attributes.length()) {
                                val attribute = attributes.optJSONObject(attributeIndex) ?: continue
                                add(
                                    TxEventAttribute(
                                        key = attribute.optString("key"),
                                        value = attribute.optString("value")
                                    )
                                )
                            }
                        }
                    )
                )
            }
        }
    )
}

private fun JSONObject.optNumber(key: String): Number {
    val value = opt(key)
    return when (value) {
        is Number -> value
        is String -> value.toLongOrNull() ?: value.toIntOrNull() ?: 0
        else -> 0
    }
}

private fun String.hexToBase64String(): String =
    chunked(2)
        .map { chunk -> chunk.toInt(16).toByte() }
        .toByteArray()
        .toBase64String()
