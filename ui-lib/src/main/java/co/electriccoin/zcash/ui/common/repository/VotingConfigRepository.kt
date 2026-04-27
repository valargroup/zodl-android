package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VotingConfigSource {
    LOCAL_OVERRIDE,
    REMOTE,
    REVIEW_OVERRIDE,
    FALLBACK
}

data class VotingConfigSnapshot(
    val session: VotingSession,
    val source: VotingConfigSource,
    val loadedAt: Instant = Instant.now()
)

interface VotingConfigRepository {
    val currentConfig: StateFlow<VotingConfigSnapshot?>

    fun store(snapshot: VotingConfigSnapshot)

    fun clear()
}

class VotingConfigRepositoryImpl : VotingConfigRepository {
    private val mutableCurrentConfig = MutableStateFlow<VotingConfigSnapshot?>(null)

    override val currentConfig: StateFlow<VotingConfigSnapshot?> = mutableCurrentConfig.asStateFlow()

    override fun store(snapshot: VotingConfigSnapshot) {
        mutableCurrentConfig.value = snapshot
    }

    override fun clear() {
        mutableCurrentConfig.value = null
    }
}
