package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class VotingKeystoneSigningBundle(
    val roundId: String,
    val roundTitle: String,
    val bundleIndex: Int,
    val bundleCount: Int,
    val actionIndex: Int,
    val encoder: UREncoder,
)

interface VotingKeystoneRepository {
    suspend fun createPcztEncoder(roundId: String): VotingKeystoneSigningBundle

    suspend fun storeBundleSignature(
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    )
}

class VotingKeystoneRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val synchronizerProvider: SynchronizerProvider,
    private val keystoneSDKProvider: KeystoneSDKProvider
) : VotingKeystoneRepository {
    override suspend fun createPcztEncoder(roundId: String): VotingKeystoneSigningBundle {
        val selectedAccount = requireNotNull(accountDataSource.getSelectedAccount() as? KeystoneAccount) {
            "Keystone account is required for voting signature flow"
        }
        val currentConfig = requireNotNull(
            votingConfigRepository.currentConfig.value ?: votingConfigRepository.get()
        ) {
            "No active voting session is loaded"
        }
        val session = currentConfig.session
        val sessionRoundId = session.voteRoundId.toLowerHex()
        require(sessionRoundId.equals(roundId, ignoreCase = true)) {
            "Round $roundId does not match active session $sessionRoundId"
        }

        val recovery = requireNotNull(votingRecoveryRepository.get(roundId)) {
            "Voting round $roundId has not been prepared"
        }
        val bundleCount = recovery.bundleCount ?: error("Voting round $roundId has no prepared bundle count")
        val hotkeySeed = recovery.decodeHotkeySeed() ?: error("Voting round $roundId has no stored hotkey seed")
        val bundleIndex = (0 until bundleCount)
            .firstOrNull { index -> index !in recovery.keystoneBundleSignatures }
            ?: error("All Keystone voting bundles are already signed for round $roundId")

        val accountIndex = selectedAccount.sdkAccount.hdAccountIndex?.index?.toInt()
            ?: error("Keystone account is missing ZIP-32 account index")
        val ufvk = selectedAccount.sdkAccount.ufvk
            ?: error("Keystone account is missing UFVK")
        val seedFingerprint = selectedAccount.sdkAccount.seedFingerprint
            ?: error("Keystone account is missing seed fingerprint")

        val synchronizer = synchronizerProvider.getSynchronizer()
        val walletDbPath = synchronizer.getWalletDbPath()
        val votingDbPath = File(walletDbPath)
            .parentFile
            ?.resolve("voting.sqlite3")
            ?.absolutePath
            ?: error("Unable to derive voting DB path from $walletDbPath")
        val networkId = synchronizer.network.toVotingNetworkId()
        val allNotesJson = votingCryptoClient.getWalletNotesJson(
            walletDbPath = walletDbPath,
            snapshotHeight = session.snapshotHeight,
            networkId = networkId,
            accountUuidBytes = selectedAccount.sdkAccount.accountUuid.value
        )

        val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
        check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

        try {
            votingCryptoClient.setWalletId(dbHandle, selectedAccount.sdkAccount.accountUuid.toString())
            val witnessesJson = votingCryptoClient.generateNoteWitnessesJson(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                walletDbPath = walletDbPath,
                notesJson = allNotesJson
            )
            val bundleNotesJson = allNotesJson.selectBundleNotesJson(witnessesJson)
            val governancePczt = votingCryptoClient.buildGovernancePczt(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                ufvk = ufvk,
                networkId = networkId,
                accountIndex = accountIndex,
                notesJson = bundleNotesJson,
                hotkeyRawSeed = hotkeySeed,
                seedFingerprint = seedFingerprint,
                roundName = session.title
            )
            val redactedPcztBytes = synchronizer.redactPcztForSigner(Pczt(governancePczt.pcztBytes))
                .toByteArray()
            return VotingKeystoneSigningBundle(
                roundId = roundId,
                roundTitle = session.title,
                bundleIndex = bundleIndex,
                bundleCount = bundleCount,
                actionIndex = governancePczt.actionIndex,
                encoder = keystoneSDKProvider.generatePczt(redactedPcztBytes)
            )
        } finally {
            votingCryptoClient.closeVotingDb(dbHandle)
        }
    }

    override suspend fun storeBundleSignature(
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    ) {
        val signedPcztBytes = keystoneSDKProvider.parsePczt(signedPcztUr)
        val spendAuthSig = votingCryptoClient.extractSpendAuthSignatureFromSignedPczt(
            signedPcztBytes = signedPcztBytes,
            actionIndex = actionIndex
        )
        val sighash = votingCryptoClient.extractPcztSighash(signedPcztBytes)
        votingRecoveryRepository.storeKeystoneBundleSignature(
            roundId = roundId,
            bundleIndex = bundleIndex,
            spendAuthSig = spendAuthSig,
            sighash = sighash
        )
    }

    private fun ZcashNetwork.toVotingNetworkId() =
        when (this) {
            ZcashNetwork.Mainnet -> 1
            ZcashNetwork.Testnet -> 0
            else -> error("Unsupported voting network: $this")
        }

    private fun String.selectBundleNotesJson(witnessesJson: String): String {
        val noteObjectsByCommitment = mutableMapOf<String, JSONObject>()
        val notes = JSONArray(this)
        for (index in 0 until notes.length()) {
            val note = notes.getJSONObject(index)
            noteObjectsByCommitment[note.getString("commitment")] = note
        }

        val witnesses = JSONArray(witnessesJson)
        return JSONArray(
            buildList {
                for (index in 0 until witnesses.length()) {
                    val witness = witnesses.getJSONObject(index)
                    val commitment = witness.getString("note_commitment")
                    add(
                        noteObjectsByCommitment[commitment]
                            ?: error("Missing note for witness commitment $commitment")
                    )
                }
            }
        ).toString()
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
