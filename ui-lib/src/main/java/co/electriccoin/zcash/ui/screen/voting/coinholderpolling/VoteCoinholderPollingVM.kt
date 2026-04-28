package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.Lce
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.effectiveChoices
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import co.electriccoin.zcash.ui.screen.voting.votingerror.VoteConfigErrorArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteCoinholderPollingVM(
    private val refreshActiveVotingSession: RefreshActiveVotingSessionUseCase,
    private val refreshVotingRounds: RefreshVotingRoundsUseCase,
    private val votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val roundsLce =
        mutableLce<List<VotingRound>>(
            votingApiRepository.snapshot.value.rounds
                .takeIf { it.isNotEmpty() }
                ?.let { rounds -> Lce(content = LceContent.Success(rounds)) }
        )
    private var configIssue: VotingConfigException? = null
    private val recoveryVoteCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var recoveryVoteCountsJob: Job? = null

    init {
        refreshRecoveryVoteCounts(votingApiRepository.snapshot.value.rounds)
        refreshVotingData()
    }

    val state =
        combine(
            votingApiRepository.snapshot,
            roundsLce.state,
            recoveryVoteCounts,
            votingSessionStore.state,
        ) { apiSnapshot, roundsLceState, persistedVoteCounts, sessionState ->
            val rounds = when {
                apiSnapshot.rounds.isNotEmpty() -> apiSnapshot.rounds
                roundsLceState.content is LceContent.Success -> emptyList()
                else -> null
            }

            rounds?.let {
                val sortedRounds = it
                    .sortedWith(
                        compareByDescending<VotingRound> { round -> round.createdAtHeight.takeIf { it > 0 } ?: round.snapshotHeight }
                            .thenByDescending { round -> round.snapshotHeight }
                            .thenByDescending { round -> round.votingEnd.epochSecond }
                            .thenBy { round -> round.id }
                    )
                val (activeSrc, pastSrc) = sortedRounds
                    .partition { round -> round.status == SessionStatus.ACTIVE }

                VoteCoinholderPollingState(
                    activeRounds = activeSrc.map { round ->
                        buildCard(
                            round = round,
                            votedProposalCount = sessionState.submittedRounds[round.id]
                                ?: persistedVoteCounts[round.id]
                        )
                    },
                    pastRounds = pastSrc.map { round ->
                        buildCard(
                            round = round,
                            votedProposalCount = sessionState.submittedRounds[round.id]
                                ?: persistedVoteCounts[round.id]
                        )
                    },
                    onBack = ::onBack
                )
            }
        }.withLce(groupLce(roundsLce)) { error ->
                errorStateMapper.mapToState(
                    error = error,
                    title = stringRes("Unable to load polls"),
                    message = stringRes("Please try again."),
                    primaryStyle = ButtonStyle.PRIMARY
                )
            }.stateIn(this)

    private fun buildCard(
        round: VotingRound,
        votedProposalCount: Int?,
    ): VotePollCardState {
        val total = round.proposals.size
        val count = votedProposalCount?.coerceIn(0, total) ?: 0
        val hasConfirmedVote = votedProposalCount != null
        val status = when {
            round.status == SessionStatus.ACTIVE && hasConfirmedVote -> VotePollCardStatus.VOTED
            round.status == SessionStatus.ACTIVE -> VotePollCardStatus.ACTIVE
            else -> VotePollCardStatus.CLOSED
        }

        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val dateLabel = when (status) {
            VotePollCardStatus.ACTIVE -> "Closes ${formatter.format(round.votingEnd)}"
            VotePollCardStatus.VOTED -> "Closes ${formatter.format(round.votingEnd)}"
            VotePollCardStatus.CLOSED -> "Closed ${formatter.format(round.votingEnd)}"
        }

        return VotePollCardState(
            roundId = round.id,
            title = stringRes(round.title),
            description = if (round.description.isNotEmpty()) {
                stringRes(round.description)
            } else {
                stringRes("")
            },
            status = status,
            sessionStatus = round.status,
            isActionEnabled = true,
            dateLabel = stringRes(dateLabel),
            votedLabel = if (hasConfirmedVote && total > 0) {
                stringRes("$count of $total voted")
            } else {
                null
            },
            proposalCount = total,
            votedCount = count,
            onAction = { onRoundSelected(round, status) }
        )
    }

    private fun refreshVotingData() {
        roundsLce.execute {
            configIssue = null
            refreshVotingRounds()
            runCatching {
                refreshActiveVotingSession()
            }.onFailure { throwable ->
                if (throwable is VotingConfigException) {
                    configIssue = throwable
                } else {
                    Log.w("VoteCoinholderPolling", "Active round refresh failed", throwable)
                }
            }
            refreshRecoveryVoteCounts(votingApiRepository.snapshot.value.rounds)
            votingApiRepository.snapshot.value.rounds
        }
    }

    private fun refreshRecoveryVoteCounts(rounds: List<VotingRound>) {
        recoveryVoteCountsJob?.cancel()
        if (rounds.isEmpty()) {
            recoveryVoteCounts.value = emptyMap()
            return
        }

        recoveryVoteCountsJob = viewModelScope.launch {
            recoveryVoteCounts.value =
                buildMap {
                    rounds.forEach { round ->
                        val recovery = votingRecoveryRepository.get(round.id) ?: return@forEach
                        if (recovery.submittedAtEpochSeconds == null) {
                            return@forEach
                        }

                        val votedCount = recovery.effectiveChoices(round.proposals).size
                        if (votedCount > 0) {
                            put(round.id, votedCount)
                        }
                    }
                }
        }
    }

    private fun onRoundSelected(
        round: VotingRound,
        status: VotePollCardStatus
    ) {
        viewModelScope.launch {
            when (status) {
                VotePollCardStatus.ACTIVE -> {
                    val issue = configIssue
                    if (issue != null) {
                        navigationRouter.forward(VoteConfigErrorArgs(issue.message.orEmpty()))
                        return@launch
                    }

                    votingSessionStore.selectRound(round.id)
                    navigationRouter.forward(
                        VoteProposalListArgs(
                            roundId = round.id,
                            mode = VoteProposalListMode.VOTING
                        )
                    )
                }

                VotePollCardStatus.VOTED -> {
                    val recovery = votingRecoveryRepository.get(round.id)
                    val draftChoices = recovery?.effectiveChoices(round.proposals).orEmpty()

                    if (draftChoices.isNotEmpty()) {
                        votingSessionStore.restoreDraftVotes(round.id, draftChoices)
                        navigationRouter.forward(
                            VoteProposalListArgs(
                                roundId = round.id,
                                mode = VoteProposalListMode.VOTED
                            )
                        )
                    } else {
                        navigateToRoundOutcome(round)
                    }
                }

                VotePollCardStatus.CLOSED -> navigateToRoundOutcome(round)
            }
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun navigateToRoundOutcome(round: VotingRound) {
        when (round.status) {
            SessionStatus.TALLYING ->
                navigationRouter.forward(VoteTallyingArgs(roundIdHex = round.id))

            else ->
                navigationRouter.forward(VoteResultsArgs(roundIdHex = round.id))
        }
    }
}
