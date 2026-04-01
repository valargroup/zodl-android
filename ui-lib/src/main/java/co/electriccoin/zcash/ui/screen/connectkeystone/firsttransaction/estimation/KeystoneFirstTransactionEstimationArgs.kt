package co.electriccoin.zcash.ui.screen.connectkeystone.firsttransaction.estimation

import kotlinx.serialization.Serializable

@Serializable
data class KeystoneFirstTransactionEstimationArgs(
    val ur: String,
    val blockHeight: Long,
)
