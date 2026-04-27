package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

enum class VotingRecoveryPhase {
    INITIALIZED,
    BUNDLES_PREPARED,
    HOTKEY_READY,
    DELEGATION_PROVED,
    DELEGATION_SUBMITTED,
    VOTES_SUBMITTED,
    SHARES_SUBMITTED
}

data class VotingProposalSelection(
    val choiceId: Int,
    val numOptions: Int
)

data class VotingKeystoneBundleSignature(
    val spendAuthSigBase64: String,
    val sighashBase64: String,
    val rkBase64: String? = null
) {
    fun decodeSpendAuthSig(): ByteArray = Base64.getDecoder().decode(spendAuthSigBase64)

    fun decodeSighash(): ByteArray = Base64.getDecoder().decode(sighashBase64)

    fun decodeRk(): ByteArray? = rkBase64?.let(Base64.getDecoder()::decode)
}

data class VotingPendingKeystoneRequest(
    val bundleIndex: Int,
    val actionIndex: Int,
    val redactedPcztBase64: String,
    val expectedSighashBase64: String,
    val expectedRkBase64: String? = null
) {
    fun decodeRedactedPczt(): ByteArray = Base64.getDecoder().decode(redactedPcztBase64)

    fun decodeExpectedSighash(): ByteArray = Base64.getDecoder().decode(expectedSighashBase64)

    fun decodeExpectedRk(): ByteArray? = expectedRkBase64?.let(Base64.getDecoder()::decode)
}

data class VotingRecoverySnapshot(
    val roundId: String,
    val phase: VotingRecoveryPhase = VotingRecoveryPhase.INITIALIZED,
    val bundleCount: Int? = null,
    val eligibleWeight: Long? = null,
    val hotkeySeedBase64: String? = null,
    val hotkeyAddress: String? = null,
    val voteServerUrls: List<String> = emptyList(),
    val singleShareMode: Boolean? = null,
    val proposalSelections: Map<Int, VotingProposalSelection> = emptyMap(),
    val keystoneBundleSignatures: Map<Int, VotingKeystoneBundleSignature> = emptyMap(),
    val pendingKeystoneRequest: VotingPendingKeystoneRequest? = null,
    val submittedProposalIds: Set<Int> = emptySet(),
    val updatedAt: Instant = Instant.now()
) {
    fun decodeHotkeySeed(): ByteArray? =
        hotkeySeedBase64?.let { encoded -> Base64.getDecoder().decode(encoded) }
}

interface VotingRecoveryRepository {
    fun observe(roundId: String): Flow<VotingRecoverySnapshot?>

    suspend fun get(roundId: String): VotingRecoverySnapshot?

    suspend fun store(snapshot: VotingRecoverySnapshot)

    suspend fun setPhase(
        roundId: String,
        phase: VotingRecoveryPhase
    )

    suspend fun storeBundleSetup(
        roundId: String,
        bundleCount: Int,
        eligibleWeight: Long
    )

    suspend fun setEligibleWeight(
        roundId: String,
        eligibleWeight: Long
    )

    suspend fun storeHotkey(
        roundId: String,
        hotkeySeed: ByteArray,
        hotkeyAddress: String
    )

    suspend fun storeVoteServerUrls(
        roundId: String,
        voteServerUrls: List<String>
    )

    suspend fun storeProposalSelections(
        roundId: String,
        proposalSelections: Map<Int, VotingProposalSelection>
    )

    suspend fun storeKeystoneBundleSignature(
        roundId: String,
        bundleIndex: Int,
        spendAuthSig: ByteArray,
        sighash: ByteArray,
        rk: ByteArray? = null
    )

    suspend fun storePendingKeystoneRequest(
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        redactedPczt: ByteArray,
        expectedSighash: ByteArray,
        expectedRk: ByteArray? = null
    )

    suspend fun clearPendingKeystoneRequest(roundId: String)

    suspend fun storeSingleShareMode(
        roundId: String,
        singleShareMode: Boolean
    )

    suspend fun markProposalSubmitted(
        roundId: String,
        proposalId: Int
    )

    suspend fun clearRound(roundId: String)
}

class VotingRecoveryRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : VotingRecoveryRepository {
    override fun observe(roundId: String): Flow<VotingRecoverySnapshot?> =
        flow {
            emitAll(
                encryptedPreferenceProvider()
                    .observe(key = key(roundId))
                    .map { encoded -> encoded?.toVotingRecoverySnapshot() }
            )
        }

    override suspend fun get(roundId: String): VotingRecoverySnapshot? =
        encryptedPreferenceProvider()
            .getString(key(roundId))
            ?.toVotingRecoverySnapshot()

    override suspend fun store(snapshot: VotingRecoverySnapshot) {
        encryptedPreferenceProvider().putString(
            key = key(snapshot.roundId),
            value = snapshot.encode()
        )
    }

    override suspend fun setPhase(
        roundId: String,
        phase: VotingRecoveryPhase
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                phase = phase,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeBundleSetup(
        roundId: String,
        bundleCount: Int,
        eligibleWeight: Long
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                phase = VotingRecoveryPhase.BUNDLES_PREPARED,
                bundleCount = bundleCount,
                eligibleWeight = eligibleWeight,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun setEligibleWeight(
        roundId: String,
        eligibleWeight: Long
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                eligibleWeight = eligibleWeight,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeHotkey(
        roundId: String,
        hotkeySeed: ByteArray,
        hotkeyAddress: String
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                phase = VotingRecoveryPhase.HOTKEY_READY,
                hotkeySeedBase64 = Base64.getEncoder().encodeToString(hotkeySeed),
                hotkeyAddress = hotkeyAddress,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeVoteServerUrls(
        roundId: String,
        voteServerUrls: List<String>
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                voteServerUrls = voteServerUrls
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map { url -> if (url.endsWith('/')) url.dropLast(1) else url }
                    .distinct(),
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeProposalSelections(
        roundId: String,
        proposalSelections: Map<Int, VotingProposalSelection>
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                proposalSelections = current.proposalSelections + proposalSelections,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeKeystoneBundleSignature(
        roundId: String,
        bundleIndex: Int,
        spendAuthSig: ByteArray,
        sighash: ByteArray,
        rk: ByteArray?
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                keystoneBundleSignatures = current.keystoneBundleSignatures + (
                    bundleIndex to VotingKeystoneBundleSignature(
                        spendAuthSigBase64 = Base64.getEncoder().encodeToString(spendAuthSig),
                        sighashBase64 = Base64.getEncoder().encodeToString(sighash),
                        rkBase64 = rk?.let(Base64.getEncoder()::encodeToString)
                    )
                ),
                pendingKeystoneRequest = current.pendingKeystoneRequest
                    ?.takeUnless { request -> request.bundleIndex == bundleIndex },
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storePendingKeystoneRequest(
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        redactedPczt: ByteArray,
        expectedSighash: ByteArray,
        expectedRk: ByteArray?
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                pendingKeystoneRequest = VotingPendingKeystoneRequest(
                    bundleIndex = bundleIndex,
                    actionIndex = actionIndex,
                    redactedPcztBase64 = Base64.getEncoder().encodeToString(redactedPczt),
                    expectedSighashBase64 = Base64.getEncoder().encodeToString(expectedSighash),
                    expectedRkBase64 = expectedRk?.let(Base64.getEncoder()::encodeToString)
                ),
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun clearPendingKeystoneRequest(roundId: String) {
        val current = get(roundId) ?: return
        if (current.pendingKeystoneRequest == null) {
            return
        }
        store(
            current.copy(
                pendingKeystoneRequest = null,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeSingleShareMode(
        roundId: String,
        singleShareMode: Boolean
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                singleShareMode = singleShareMode,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun markProposalSubmitted(
        roundId: String,
        proposalId: Int
    ) {
        val current = get(roundId) ?: VotingRecoverySnapshot(roundId = roundId)
        store(
            current.copy(
                submittedProposalIds = current.submittedProposalIds + proposalId,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun clearRound(roundId: String) {
        encryptedPreferenceProvider().remove(key(roundId))
    }

    private fun key(roundId: String) = PreferenceKey("voting_recovery_${roundId.lowercase()}")
}

private fun VotingRecoverySnapshot.encode(): String =
    JSONObject()
        .put("round_id", roundId)
        .put("phase", phase.name)
        .put("bundle_count", bundleCount)
        .put("eligible_weight", eligibleWeight)
        .put("hotkey_seed", hotkeySeedBase64)
        .put("hotkey_address", hotkeyAddress)
        .put("vote_server_urls", JSONArray(voteServerUrls))
        .put("single_share_mode", singleShareMode)
        .put(
            "proposal_selections",
            JSONObject().apply {
                proposalSelections.toSortedMap().forEach { (proposalId, selection) ->
                    put(
                        proposalId.toString(),
                        JSONObject()
                            .put("choice_id", selection.choiceId)
                            .put("num_options", selection.numOptions)
                    )
                }
            }
        )
        .put(
            "keystone_bundle_signatures",
            JSONObject().apply {
                keystoneBundleSignatures.toSortedMap().forEach { (bundleIndex, signature) ->
                    put(
                        bundleIndex.toString(),
                        JSONObject()
                            .put("spend_auth_sig", signature.spendAuthSigBase64)
                            .put("sighash", signature.sighashBase64)
                            .put("rk", signature.rkBase64)
                    )
                }
            }
        )
        .put(
            "pending_keystone_request",
            pendingKeystoneRequest?.let { request ->
                JSONObject()
                    .put("bundle_index", request.bundleIndex)
                    .put("action_index", request.actionIndex)
                    .put("redacted_pczt", request.redactedPcztBase64)
                    .put("expected_sighash", request.expectedSighashBase64)
                    .put("expected_rk", request.expectedRkBase64)
            }
        )
        .put("submitted_proposal_ids", JSONArray(submittedProposalIds.sorted()))
        .put("updated_at", updatedAt.toEpochMilli())
        .toString()

private fun String.toVotingRecoverySnapshot(): VotingRecoverySnapshot {
    val json = JSONObject(this)

    return VotingRecoverySnapshot(
        roundId = json.getString("round_id"),
        phase = json.optString("phase")
            .takeIf { it.isNotEmpty() }
            ?.let(VotingRecoveryPhase::valueOf)
            ?: VotingRecoveryPhase.INITIALIZED,
        bundleCount = json.optInt("bundle_count")
            .takeIf { json.has("bundle_count") && !json.isNull("bundle_count") },
        eligibleWeight = json.optLong("eligible_weight")
            .takeIf { json.has("eligible_weight") && !json.isNull("eligible_weight") },
        hotkeySeedBase64 = json.optString("hotkey_seed").takeIf { it.isNotEmpty() },
        hotkeyAddress = json.optString("hotkey_address").takeIf { it.isNotEmpty() },
        voteServerUrls = buildList {
            val voteServersJson = json.optJSONArray("vote_server_urls") ?: JSONArray()
            for (index in 0 until voteServersJson.length()) {
                add(voteServersJson.getString(index))
            }
        },
        singleShareMode = json.optBoolean("single_share_mode")
            .takeIf { json.has("single_share_mode") && !json.isNull("single_share_mode") },
        proposalSelections = buildMap {
            val selectionsJson = json.optJSONObject("proposal_selections") ?: JSONObject()
            selectionsJson.keys().forEach { proposalId ->
                val selection = selectionsJson.optJSONObject(proposalId) ?: return@forEach
                put(
                    proposalId.toInt(),
                    VotingProposalSelection(
                        choiceId = selection.getInt("choice_id"),
                        numOptions = selection.getInt("num_options")
                    )
                )
            }
        },
        keystoneBundleSignatures = buildMap {
            val signaturesJson = json.optJSONObject("keystone_bundle_signatures") ?: JSONObject()
            signaturesJson.keys().forEach { bundleIndex ->
                val signature = signaturesJson.optJSONObject(bundleIndex) ?: return@forEach
                put(
                    bundleIndex.toInt(),
                    VotingKeystoneBundleSignature(
                        spendAuthSigBase64 = signature.getString("spend_auth_sig"),
                        sighashBase64 = signature.getString("sighash"),
                        rkBase64 = signature.optString("rk").takeIf(String::isNotEmpty)
                    )
                )
            }
        },
        pendingKeystoneRequest = json.optJSONObject("pending_keystone_request")
            ?.let { request ->
                VotingPendingKeystoneRequest(
                    bundleIndex = request.getInt("bundle_index"),
                    actionIndex = request.getInt("action_index"),
                    redactedPcztBase64 = request.getString("redacted_pczt"),
                    expectedSighashBase64 = request.getString("expected_sighash"),
                    expectedRkBase64 = request.optString("expected_rk").takeIf(String::isNotEmpty)
                )
            },
        submittedProposalIds = buildSet {
            val submittedIds = json.optJSONArray("submitted_proposal_ids") ?: JSONArray()
            for (index in 0 until submittedIds.length()) {
                add(submittedIds.getInt(index))
            }
        },
        updatedAt = json.optLong("updated_at")
            .takeIf { json.has("updated_at") }
            ?.let(Instant::ofEpochMilli)
            ?: Instant.now()
    )
}
