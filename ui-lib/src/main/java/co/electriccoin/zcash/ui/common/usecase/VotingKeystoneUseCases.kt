package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import com.sparrowwallet.hummingbird.UR

class CreateVotingKeystonePcztEncoderUseCase(
    private val votingKeystoneRepository: VotingKeystoneRepository
) {
    suspend operator fun invoke(roundId: String): VotingKeystoneSigningBundle =
        votingKeystoneRepository.createPcztEncoder(roundId)
}

class ParseVotingKeystonePCZTUseCase(
    private val votingKeystoneRepository: VotingKeystoneRepository,
    keystoneSDKProvider: KeystoneSDKProvider,
) : BaseKeystoneScanner(keystoneSDKProvider) {
    suspend operator fun invoke(
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        result: String
    ): ParseKeystoneQrResult {
        this.roundId = roundId
        this.bundleIndex = bundleIndex
        this.actionIndex = actionIndex
        return super.invoke(result)
    }

    private var roundId: String? = null
    private var bundleIndex: Int? = null
    private var actionIndex: Int? = null

    override suspend fun onSuccess(ur: UR) {
        votingKeystoneRepository.storeBundleSignature(
            roundId = requireNotNull(roundId),
            bundleIndex = requireNotNull(bundleIndex),
            actionIndex = requireNotNull(actionIndex),
            signedPcztUr = ur
        )
    }
}
