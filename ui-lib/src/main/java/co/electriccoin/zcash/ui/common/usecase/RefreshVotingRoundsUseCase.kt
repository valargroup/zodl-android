package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository

class RefreshVotingRoundsUseCase(
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
) {
    suspend operator fun invoke() {
        votingApiProvider.fetchServiceConfig()
        val rounds = votingApiProvider.fetchAllRounds()
        votingApiRepository.storeRounds(rounds)
    }
}
