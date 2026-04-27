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

data class VotingRecoverySnapshot(
    val roundId: String,
    val phase: VotingRecoveryPhase = VotingRecoveryPhase.INITIALIZED,
    val bundleCount: Int? = null,
    val eligibleWeight: Long? = null,
    val hotkeySeedBase64: String? = null,
    val hotkeyAddress: String? = null,
    val singleShareMode: Boolean? = null,
    val proposalSelections: Map<Int, VotingProposalSelection> = emptyMap(),
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

    suspend fun storeProposalSelections(
        roundId: String,
        proposalSelections: Map<Int, VotingProposalSelection>
    )

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
