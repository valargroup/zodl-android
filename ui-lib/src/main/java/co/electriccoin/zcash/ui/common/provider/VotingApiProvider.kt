package co.electriccoin.zcash.ui.common.provider

import android.util.Log
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.ChainActiveRoundResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundsResponse
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.ShareConfirmationResult
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.RoundAuthStatus
import co.electriccoin.zcash.ui.common.model.voting.RoundAuthenticator
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.model.voting.ChainTallyResultsResponse
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundAuthenticationException
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.retainingRoundsWithValidSignatures
import co.electriccoin.zcash.ui.common.model.voting.toTallyResults
import co.electriccoin.zcash.ui.common.model.voting.toBase64String
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

interface VotingApiProvider {
    suspend fun fetchServiceConfig(): VotingServiceConfig

    suspend fun fetchActiveVotingSession(): VotingSession?

    suspend fun fetchAllRounds(): List<VotingRound>

    suspend fun submitDelegation(registration: DelegationRegistration): TxResult

    suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ): TxResult

    suspend fun fetchTallyResults(roundIdHex: String): TallyResults

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
        candidateUrls: List<String>,
        excludeUrls: List<String>
    ): List<String>

    suspend fun fetchTxConfirmation(txHash: String): TxConfirmation?
}

class KtorVotingApiProvider(
    private val httpClientProvider: HttpClientProvider,
    private val configurationRepository: ConfigurationRepository,
) : VotingApiProvider {
    private var cachedResolvedConfig: ResolvedVotingConfig? = null
    private val configMutex = Mutex()
    private val serverHealthTracker = VotingServerHealthTracker()

    override suspend fun fetchServiceConfig(): VotingServiceConfig =
        getResolvedConfig(forceRefresh = true).serviceConfig

    override suspend fun fetchActiveVotingSession(): VotingSession? =
        execute {
            val baseUrl = resolveBaseUrl() ?: return@execute null
            try {
                val response = get("$baseUrl/shielded-vote/v1/rounds/active").body<ChainActiveRoundResponse>()
                response.round?.toVotingSession()?.let { session ->
                    authenticateVotingSession(session)
                }
            } catch (responseException: ResponseException) {
                if (
                    responseException.response.status == HttpStatusCode.NotFound ||
                    responseException.isNoActiveRoundResponse()
                ) {
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
            response.rounds
                ?.mapNotNull { dto ->
                    authenticateVotingSessionOrNull(dto.toVotingSession())?.let { dto.toVotingRound() }
                }
                ?: emptyList()
        }

    override suspend fun submitDelegation(registration: DelegationRegistration): TxResult =
        execute {
            val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
            postTxResult(
                url = "$baseUrl/shielded-vote/v1/delegate-vote",
                body = registration.toApiBody()
            )
        }

    override suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ): TxResult = execute {
        val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
        postTxResult(
            url = "$baseUrl/shielded-vote/v1/cast-vote",
            body = bundle.toApiBody(signature)
        )
    }

    override suspend fun fetchTallyResults(roundIdHex: String): TallyResults =
        execute {
            val baseUrl = resolveBaseUrl() ?: error("Voting server URL is not configured")
            get("$baseUrl/shielded-vote/v1/tally-results/$roundIdHex")
                .body<ChainTallyResultsResponse>()
                .toTallyResults(roundIdHex)
        }

    override suspend fun delegateShares(
        shares: List<SharePayload>,
        roundIdHex: String
    ): List<DelegatedShareInfo> = execute {
        if (shares.isEmpty()) {
            return@execute emptyList()
        }

        val config = getResolvedConfig().serviceConfig
        val serverUrls = config.voteServers
            .map { endpoint -> endpoint.url.trimEnd('/') }
            .distinct()

        if (serverUrls.isEmpty()) {
            error("Voting server URL is not configured")
        }

        serverHealthTracker.remember(serverUrls)

        buildList {
            for (share in shares) {
                val body = share.toApiBody(roundIdHex)
                val healthyServers = serverHealthTracker.healthyServers(serverUrls)
                val quorum = max(1, (healthyServers.size + 1) / 2)
                val targets = healthyServers.shuffled().take(quorum)
                val acceptedByServers = postShareToTargets(targets, body).toMutableList()
                if (acceptedByServers.isEmpty()) {
                    val fallbackTargets = serverHealthTracker.healthyServers(serverUrls)
                        .filterNot { serverUrl -> serverUrl in targets }
                        .shuffled()
                    for (fallbackTarget in fallbackTargets) {
                        if (postShare(fallbackTarget, body)) {
                            acceptedByServers += fallbackTarget
                            break
                        }
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
        val normalizedHelperBaseUrl = helperBaseUrl.trimEnd('/')
        try {
            val responseJson = get(
                "$normalizedHelperBaseUrl/shielded-vote/v1/share-status/$roundIdHex/$nullifierHex"
            ) {
                header("Accept", "application/json")
                header("X-Helper-Token", "voting-helper")
                timeout {
                    requestTimeoutMillis = HELPER_REQUEST_TIMEOUT_MILLIS
                    socketTimeoutMillis = HELPER_SOCKET_TIMEOUT_MILLIS
                    connectTimeoutMillis = HELPER_CONNECT_TIMEOUT_MILLIS
                }
            }.bodyAsText()
            serverHealthTracker.recordSuccess(normalizedHelperBaseUrl)
            when (JSONObject(responseJson).optString("status")) {
                "confirmed" -> ShareConfirmationResult.CONFIRMED
                else -> ShareConfirmationResult.PENDING
            }
        } catch (throwable: Throwable) {
            serverHealthTracker.recordFailure(normalizedHelperBaseUrl)
            throw throwable
        }
    }

    override suspend fun resubmitShare(
        payload: SharePayload,
        roundIdHex: String,
        candidateUrls: List<String>,
        excludeUrls: List<String>
    ): List<String> = execute {
        val allServers = candidateUrls.normalizeServerUrls()
        val excludedServers = excludeUrls.normalizeServerUrls().toSet()
        if (allServers.isEmpty()) {
            return@execute emptyList()
        }
        serverHealthTracker.remember(allServers)

        val healthyServers = serverHealthTracker.healthyServers(allServers)
        val candidateServers = healthyServers.filterNot { serverUrl -> serverUrl in excludedServers }
        val body = payload.withSubmitAt(0).toApiBody(roundIdHex)

        if (candidateServers.isEmpty()) {
            for (serverUrl in healthyServers.shuffled()) {
                if (postShare(serverUrl, body)) {
                    return@execute listOf(serverUrl)
                }
            }
            return@execute emptyList()
        }

        val quorum = max(1, (candidateServers.size + 1) / 2)
        postShareToTargets(candidateServers.shuffled().take(quorum), body)
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

    private suspend fun getResolvedConfig(forceRefresh: Boolean = false): ResolvedVotingConfig =
        configMutex.withLock {
            val cached = cachedResolvedConfig
            if (!forceRefresh && cached != null) {
                cached
            } else {
                resolveConfig().also { resolved ->
                    cachedResolvedConfig = resolved
                }
            }
        }

    private suspend fun resolveConfig(): ResolvedVotingConfig {
        val configuration = configurationRepository.configurationFlow.value
        val configUrl = configuration?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue).orEmpty()

        if (configUrl.isNotEmpty()) {
            return fetchTrustedConfig(PinnedConfigSource.parse(configUrl))
        }

        return fetchTrustedConfig(PinnedConfigSource.parse(StaticVotingConfig.BUNDLED_PINNED_SOURCE))
    }

    private suspend fun resolveBaseUrl(): String? {
        val config = getResolvedConfig().serviceConfig
        return config.voteServers.firstOrNull()?.url?.trimEnd('/')
    }

    private suspend fun fetchTrustedConfig(source: PinnedConfigSource): ResolvedVotingConfig {
        val staticConfig = execute {
            val bytes = try {
                get(source.url) {
                    noCache()
                }.bodyAsBytes()
            } catch (responseException: ResponseException) {
                throw VotingConfigException(
                    "Static voting config fetch failed: HTTP ${responseException.response.status.value}"
                )
            }
            StaticVotingConfig.decodeAndVerify(
                data = bytes,
                expectedSHA256 = source.sha256
            )
        }
        val rawServiceConfig = execute {
            try {
                get(staticConfig.dynamicConfigURL) {
                    noCache()
                }.bodyAsText()
            } catch (responseException: ResponseException) {
                throw VotingConfigException(
                    "Dynamic voting config fetch failed: HTTP ${responseException.response.status.value}"
                )
            }
        }.let(VotingServiceConfig::decode)
            .also(VotingServiceConfig::validate)
        val serviceConfig = rawServiceConfig.retainingRoundsWithValidSignatures(staticConfig.trustedKeys)

        return ResolvedVotingConfig(
            staticConfig = staticConfig,
            rawServiceConfig = rawServiceConfig,
            serviceConfig = serviceConfig
        )
    }

    private suspend fun authenticateVotingSession(session: VotingSession): VotingSession {
        val resolvedConfig = getResolvedConfig()
        val roundIdHex = session.voteRoundId.toLowerHex()
        val status = RoundAuthenticator.authenticate(
            chainEaPK = session.eaPK,
            roundIdHex = roundIdHex,
            rounds = resolvedConfig.rawServiceConfig.rounds,
            trustedKeys = resolvedConfig.staticConfig.trustedKeys
        )
        if (status != RoundAuthStatus.AUTHENTICATED) {
            throw VotingRoundAuthenticationException(status = status, roundIdHex = roundIdHex)
        }
        return session
    }

    private suspend fun authenticateVotingSessionOrNull(session: VotingSession): VotingSession? =
        try {
            authenticateVotingSession(session)
        } catch (exception: VotingRoundAuthenticationException) {
            Log.w(
                TAG,
                "Skipping unauthenticated voting round ${exception.roundIdHex}: ${exception.status}"
            )
            null
        }

    private suspend fun HttpClient.postTxResult(
        url: String,
        body: String
    ): TxResult =
        try {
            post(url) {
                setBody(TextContent(body, ContentType.Application.Json))
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

    private suspend fun HttpClient.postShareToTargets(
        targetUrls: List<String>,
        body: String
    ): List<String> =
        coroutineScope {
            targetUrls
                .map { targetUrl ->
                    async {
                        if (postShare(targetUrl, body)) {
                            targetUrl
                        } else {
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }

    private suspend fun HttpClient.postShare(
        serverUrl: String,
        body: String
    ): Boolean =
        try {
            post("$serverUrl/shielded-vote/v1/shares") {
                setBody(TextContent(body, ContentType.Application.Json))
                timeout {
                    requestTimeoutMillis = HELPER_REQUEST_TIMEOUT_MILLIS
                    socketTimeoutMillis = HELPER_SOCKET_TIMEOUT_MILLIS
                    connectTimeoutMillis = HELPER_CONNECT_TIMEOUT_MILLIS
                }
            }
            serverHealthTracker.recordSuccess(serverUrl)
            true
        } catch (_: Throwable) {
            serverHealthTracker.recordFailure(serverUrl)
            false
        }
}

private class VotingServerHealthTracker {
    private val mutex = Mutex()
    private val statesByUrl = mutableMapOf<String, ServerState>()

    suspend fun remember(serverUrls: List<String>) {
        val normalizedUrls = serverUrls.normalizeServerUrls()
        mutex.withLock {
            normalizedUrls.forEach { serverUrl ->
                statesByUrl.putIfAbsent(serverUrl, ServerState())
            }
        }
    }

    suspend fun healthyServers(serverUrls: List<String>): List<String> {
        val normalizedUrls = serverUrls.normalizeServerUrls()
        if (normalizedUrls.isEmpty()) {
            return emptyList()
        }
        val nowMillis = System.currentTimeMillis()
        return mutex.withLock {
            val healthyServers = normalizedUrls.filter { serverUrl ->
                val state = statesByUrl.getOrPut(serverUrl, ::ServerState)
                when (val circuit = state.circuit) {
                    Circuit.CLOSED,
                    Circuit.HALF_OPEN -> true

                    is Circuit.OPEN ->
                        if (nowMillis - circuit.sinceMillis >= COOLDOWN_INTERVAL_MILLIS) {
                            state.circuit = Circuit.HALF_OPEN
                            true
                        } else {
                            false
                        }
                }
            }
            healthyServers.ifEmpty { normalizedUrls }
        }
    }

    suspend fun recordSuccess(serverUrl: String) {
        val normalizedServerUrl = serverUrl.trimEnd('/')
        if (normalizedServerUrl.isEmpty()) {
            return
        }
        mutex.withLock {
            statesByUrl.getOrPut(normalizedServerUrl, ::ServerState).apply {
                circuit = Circuit.CLOSED
                consecutiveFailures = 0
            }
        }
    }

    suspend fun recordFailure(serverUrl: String) {
        val normalizedServerUrl = serverUrl.trimEnd('/')
        if (normalizedServerUrl.isEmpty()) {
            return
        }
        val nowMillis = System.currentTimeMillis()
        mutex.withLock {
            val state = statesByUrl.getOrPut(normalizedServerUrl, ::ServerState)
            state.consecutiveFailures += 1
            when (state.circuit) {
                Circuit.HALF_OPEN -> state.circuit = Circuit.OPEN(nowMillis)
                Circuit.CLOSED ->
                    if (state.consecutiveFailures >= FAILURE_THRESHOLD) {
                        state.circuit = Circuit.OPEN(nowMillis)
                    }

                is Circuit.OPEN -> Unit
            }
        }
    }

    private class ServerState(
        var circuit: Circuit = Circuit.CLOSED,
        var consecutiveFailures: Int = 0
    )

    private sealed interface Circuit {
        data object CLOSED : Circuit

        data class OPEN(val sinceMillis: Long) : Circuit

        data object HALF_OPEN : Circuit
    }

    private companion object {
        const val FAILURE_THRESHOLD = 3
        const val COOLDOWN_INTERVAL_MILLIS = 30_000L
    }
}

private data class ResolvedVotingConfig(
    val staticConfig: StaticVotingConfig,
    val rawServiceConfig: VotingServiceConfig,
    val serviceConfig: VotingServiceConfig,
)

private fun HttpRequestBuilder.noCache() {
    header("Cache-Control", "no-cache")
    header("Pragma", "no-cache")
}

private const val HELPER_REQUEST_TIMEOUT_MILLIS = 5_000L
private const val HELPER_SOCKET_TIMEOUT_MILLIS = 10_000L
private const val HELPER_CONNECT_TIMEOUT_MILLIS = 5_000L
private const val TAG = "VotingApiProvider"

private fun List<String>.normalizeServerUrls(): List<String> =
    map(String::trim)
        .filter(String::isNotEmpty)
        .map { serverUrl -> serverUrl.trimEnd('/') }
        .distinct()

private fun SharePayload.toApiBody(roundIdHex: String) =
    JSONObject()
        .put("shares_hash", sharesHash.toBase64String())
        .put("proposal_id", proposalId)
        .put("vote_decision", voteDecision)
        .put(
            "enc_share",
            JSONObject()
                .put("c1", encShare.c1.toBase64String())
                .put("c2", encShare.c2.toBase64String())
                .put("share_index", encShare.shareIndex)
        )
        .put("share_index", encShare.shareIndex)
        .put("tree_position", treePosition)
        .put("vote_round_id", roundIdHex)
        .put(
            "all_enc_shares",
            org.json.JSONArray(
                allEncShares.map { share ->
                    JSONObject()
                        .put("c1", share.c1.toBase64String())
                        .put("c2", share.c2.toBase64String())
                        .put("share_index", share.shareIndex)
                }
            )
        )
        .put("share_comms", org.json.JSONArray(shareComms.map(ByteArray::toBase64String)))
        .put("primary_blind", primaryBlind.toBase64String())
        .put("submit_at", submitAt)
        .toString()

private fun DelegationRegistration.toApiBody(): String =
    JSONObject()
        .put("rk", rk.toBase64String())
        .put("spend_auth_sig", spendAuthSig.toBase64String())
        .put("sighash", sighash.toBase64String())
        .put("signed_note_nullifier", signedNoteNullifier.toBase64String())
        .put("cmx_new", cmxNew.toBase64String())
        .put("van_cmx", vanCmx.toBase64String())
        .put("gov_nullifiers", org.json.JSONArray(govNullifiers.map(ByteArray::toBase64String)))
        .put("proof", proof.toBase64String())
        .put("vote_round_id", voteRoundId.toBase64String())
        .toString()

private fun VoteCommitmentBundle.toApiBody(signature: CastVoteSignature): String =
    JSONObject()
        .put("van_nullifier", vanNullifier.toBase64String())
        .put("vote_authority_note_new", voteAuthorityNoteNew.toBase64String())
        .put("vote_commitment", voteCommitment.toBase64String())
        .put("proposal_id", proposalId)
        .put("proof", proof.toBase64String())
        .put("vote_round_id", voteRoundId.hexToBase64String())
        .put("vote_comm_tree_anchor_height", anchorHeight)
        .put("r_vpk", rVpkBytes.toBase64String())
        .put("vote_auth_sig", signature.voteAuthSig.toBase64String())
        .toString()

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

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private suspend fun ResponseException.isNoActiveRoundResponse(): Boolean {
    if (response.status != HttpStatusCode.InternalServerError) {
        return false
    }

    val responseText = runCatching { response.bodyAsText() }
        .getOrNull()
        ?.lowercase()
        ?: return false

    return "no active voting round" in responseText && "key not found" in responseText
}
