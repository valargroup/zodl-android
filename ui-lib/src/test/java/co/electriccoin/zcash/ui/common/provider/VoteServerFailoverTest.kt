package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

class VoteServerFailoverTest {
    @Test
    fun firstVoteServerFailureFallsThroughToSecondServer() = runBlocking {
        val triedServers = mutableListOf<String>()

        val result = withVoteServerFailover(
            path = "/shielded-vote/v1/rounds",
            serverUrls = listOf(" https://first.example.com/ ", "https://second.example.com")
        ) { serverUrl ->
            triedServers += serverUrl
            if (serverUrl == "https://first.example.com") {
                error("first server unavailable")
            }
            "rounds"
        }

        assertEquals("rounds", result)
        assertEquals(
            listOf("https://first.example.com", "https://second.example.com"),
            triedServers
        )
    }

    @Test
    fun allVoteServersFailedReturnsStableTypedError() {
        val exception = assertFailsWith<VotingServerFailoverException> {
            runBlocking {
                withVoteServerFailover(
                    path = "/shielded-vote/v1/tally-results/abc",
                    serverUrls = listOf("https://first.example.com", "https://second.example.com")
                ) {
                    error("server unavailable")
                }
            }
        }

        assertEquals("/shielded-vote/v1/tally-results/abc", exception.path)
        assertEquals(
            listOf("https://first.example.com", "https://second.example.com"),
            exception.serverUrls
        )
        assertEquals("server unavailable", exception.lastError?.message)
    }

    @Test
    fun nonRetryableConfigFailureDoesNotTryNextServer() {
        val triedServers = mutableListOf<String>()
        val expected = VotingConfigException("round authentication failed")

        val exception = assertFailsWith<VotingConfigException> {
            runBlocking {
                withVoteServerFailover(
                    path = "/shielded-vote/v1/rounds/active",
                    serverUrls = listOf("https://first.example.com", "https://second.example.com")
                ) { serverUrl ->
                    triedServers += serverUrl
                    throw expected
                }
            }
        }

        assertSame(expected, exception)
        assertEquals(listOf("https://first.example.com"), triedServers)
    }
}
