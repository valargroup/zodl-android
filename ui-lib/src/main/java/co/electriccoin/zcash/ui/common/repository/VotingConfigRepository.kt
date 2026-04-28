package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.hexStringToBytes
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class VotingConfigSource {
    LOCAL_OVERRIDE,
    REMOTE,
    REVIEW_OVERRIDE,
    FALLBACK
}

data class VotingConfigSnapshot(
    val session: VotingSession,
    val serviceConfig: VotingServiceConfig,
    val source: VotingConfigSource,
    val loadedAt: Instant = Instant.now()
)

interface VotingConfigRepository {
    val currentConfig: StateFlow<VotingConfigSnapshot?>

    suspend fun get(): VotingConfigSnapshot?

    fun observe(): Flow<VotingConfigSnapshot?>

    suspend fun store(snapshot: VotingConfigSnapshot)

    suspend fun clear()
}

class VotingConfigRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : VotingConfigRepository {
    private val mutableCurrentConfig = MutableStateFlow<VotingConfigSnapshot?>(null)

    override val currentConfig: StateFlow<VotingConfigSnapshot?> = mutableCurrentConfig.asStateFlow()

    override suspend fun get(): VotingConfigSnapshot? {
        currentConfig.value?.let { cached -> return cached }

        val restored = encryptedPreferenceProvider()
            .getString(PREFERENCE_KEY)
            ?.toVotingConfigSnapshot()
        mutableCurrentConfig.value = restored
        return restored
    }

    override fun observe(): Flow<VotingConfigSnapshot?> =
        flow {
            emitAll(
                encryptedPreferenceProvider()
                    .observe(PREFERENCE_KEY)
                    .map { encoded -> encoded?.toVotingConfigSnapshot() }
            )
        }

    override suspend fun store(snapshot: VotingConfigSnapshot) {
        encryptedPreferenceProvider().putString(
            key = PREFERENCE_KEY,
            value = snapshot.encode()
        )
        mutableCurrentConfig.value = snapshot
    }

    override suspend fun clear() {
        encryptedPreferenceProvider().remove(PREFERENCE_KEY)
        mutableCurrentConfig.value = null
    }

    private companion object {
        val PREFERENCE_KEY = PreferenceKey("voting_current_config")
    }
}

private fun VotingConfigSnapshot.encode(): String =
    JSONObject()
        .put("source", source.name)
        .put("loaded_at", loadedAt.toEpochMilli())
        .put("service_config", JSONObject(serviceConfig.encode()))
        .put("session", session.encode())
        .toString()

private fun VotingSession.encode(): JSONObject =
    JSONObject()
        .put("vote_round_id", voteRoundId.toLowerHex())
        .put("snapshot_height", snapshotHeight)
        .put("snapshot_blockhash", snapshotBlockhash.toLowerHex())
        .put("proposals_hash", proposalsHash.toLowerHex())
        .put("vote_end_time", voteEndTime.toEpochMilli())
        .put("ceremony_start", ceremonyStart.toEpochMilli())
        .put("ea_pk", eaPK.toLowerHex())
        .put("vk_zkp1", vkZkp1.toLowerHex())
        .put("vk_zkp2", vkZkp2.toLowerHex())
        .put("vk_zkp3", vkZkp3.toLowerHex())
        .put("nc_root", ncRoot.toLowerHex())
        .put("nullifier_imt_root", nullifierIMTRoot.toLowerHex())
        .put("creator", creator)
        .put("title", title)
        .put("description", description)
        .put("discussion_url", discussionUrl)
        .put("status", status.name)
        .put("created_at_height", createdAtHeight)
        .put(
            "proposals",
            JSONArray(
                proposals.map(Proposal::encode)
            )
        )

private fun Proposal.encode(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("zip_number", zipNumber)
        .put("forum_url", forumUrl)
        .put(
            "options",
            JSONArray(
                options.map(VoteOption::encode)
            )
        )

private fun VoteOption.encode(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("label", label)

private fun String.toVotingConfigSnapshot(): VotingConfigSnapshot {
    val json = JSONObject(this)
    return VotingConfigSnapshot(
        session = json.getJSONObject("session").toVotingSession(),
        serviceConfig = json.optJSONObject("service_config")
            ?.toString()
            ?.let(VotingServiceConfig::decode)
            ?: VotingServiceConfig.EMPTY,
        source = json.optString("source")
            .takeIf(String::isNotEmpty)
            ?.let(VotingConfigSource::valueOf)
            ?: VotingConfigSource.REMOTE,
        loadedAt = json.optLong("loaded_at")
            .takeIf { json.has("loaded_at") && !json.isNull("loaded_at") }
            ?.let(Instant::ofEpochMilli)
            ?: Instant.now()
    )
}

private fun JSONObject.toVotingSession(): VotingSession =
    VotingSession(
        voteRoundId = getString("vote_round_id").hexStringToBytes(),
        snapshotHeight = getLong("snapshot_height"),
        snapshotBlockhash = optString("snapshot_blockhash").hexStringToBytes(),
        proposalsHash = getString("proposals_hash").hexStringToBytes(),
        voteEndTime = Instant.ofEpochMilli(getLong("vote_end_time")),
        ceremonyStart = Instant.ofEpochMilli(getLong("ceremony_start")),
        eaPK = getString("ea_pk").hexStringToBytes(),
        vkZkp1 = getString("vk_zkp1").hexStringToBytes(),
        vkZkp2 = getString("vk_zkp2").hexStringToBytes(),
        vkZkp3 = getString("vk_zkp3").hexStringToBytes(),
        ncRoot = getString("nc_root").hexStringToBytes(),
        nullifierIMTRoot = getString("nullifier_imt_root").hexStringToBytes(),
        creator = optString("creator"),
        title = optString("title"),
        description = optString("description"),
        discussionUrl = optString("discussion_url").takeIf(String::isNotEmpty),
        proposals = buildList {
            val proposalsJson = optJSONArray("proposals") ?: JSONArray()
            for (index in 0 until proposalsJson.length()) {
                add(proposalsJson.getJSONObject(index).toProposal())
            }
        },
        status = optString("status")
            .takeIf(String::isNotEmpty)
            ?.let(SessionStatus::valueOf)
            ?: SessionStatus.ACTIVE,
        createdAtHeight = optLong("created_at_height")
    )

private fun JSONObject.toProposal(): Proposal =
    Proposal(
        id = getInt("id"),
        title = optString("title"),
        description = optString("description"),
        options = buildList {
            val optionsJson = optJSONArray("options") ?: JSONArray()
            for (index in 0 until optionsJson.length()) {
                add(optionsJson.getJSONObject(index).toVoteOption())
            }
        },
        zipNumber = optString("zip_number").takeIf(String::isNotEmpty),
        forumUrl = optString("forum_url").takeIf(String::isNotEmpty)
    )

private fun JSONObject.toVoteOption(): VoteOption =
    VoteOption(
        id = getInt("id"),
        label = optString("label")
    )

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
