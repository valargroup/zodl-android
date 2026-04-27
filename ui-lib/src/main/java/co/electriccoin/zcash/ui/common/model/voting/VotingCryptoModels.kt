package co.electriccoin.zcash.ui.common.model.voting

private fun List<ByteArray>.contentListEquals(other: List<ByteArray>) =
    size == other.size && indices.all { this[it].contentEquals(other[it]) }

private fun List<ByteArray>.contentListHashCode() =
    fold(1) { acc, value -> 31 * acc + value.contentHashCode() }

data class VotingBundleSetupResult(
    val bundleCount: Int,
    val eligibleWeight: Long
)

data class VotingHotkey(
    val secretKey: ByteArray,
    val publicKey: ByteArray,
    val address: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingHotkey) return false

        return secretKey.contentEquals(other.secretKey) &&
            publicKey.contentEquals(other.publicKey) &&
            address == other.address
    }

    override fun hashCode(): Int {
        var result = secretKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + address.hashCode()
        return result
    }
}

data class VotingGovernancePczt(
    val pcztBytes: ByteArray,
    val sighash: ByteArray,
    val actionIndex: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingGovernancePczt) return false

        return sighash.contentEquals(other.sighash)
    }

    override fun hashCode(): Int = sighash.contentHashCode()
}

data class VotingDelegationProof(
    val proof: ByteArray,
    val publicInputs: List<ByteArray>,
    val nfSigned: ByteArray,
    val cmxNew: ByteArray,
    val govNullifiers: List<ByteArray>,
    val vanComm: ByteArray,
    val rk: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingDelegationProof) return false

        return proof.contentEquals(other.proof) &&
            publicInputs.contentListEquals(other.publicInputs) &&
            govNullifiers.contentListEquals(other.govNullifiers)
    }

    override fun hashCode(): Int {
        var result = proof.contentHashCode()
        result = 31 * result + publicInputs.contentListHashCode()
        result = 31 * result + govNullifiers.contentListHashCode()
        return result
    }
}

data class VotingDelegationSubmission(
    val proof: ByteArray,
    val rk: ByteArray,
    val spendAuthSig: ByteArray,
    val sighash: ByteArray,
    val nfSigned: ByteArray,
    val cmxNew: ByteArray,
    val govComm: ByteArray,
    val govNullifiers: List<ByteArray>,
    val alpha: ByteArray,
    val voteRoundId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingDelegationSubmission) return false

        return sighash.contentEquals(other.sighash)
    }

    override fun hashCode(): Int = sighash.contentHashCode()
}

data class VotingVoteCommitment(
    val vanNullifier: ByteArray,
    val voteAuthorityNoteNew: ByteArray,
    val voteCommitment: ByteArray,
    val rVpk: ByteArray,
    val alphaV: ByteArray,
    val anchorHeight: Int,
    val encSharesJson: String,
    val rawBundleJson: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingVoteCommitment) return false

        return voteCommitment.contentEquals(other.voteCommitment)
    }

    override fun hashCode(): Int = voteCommitment.contentHashCode()
}
