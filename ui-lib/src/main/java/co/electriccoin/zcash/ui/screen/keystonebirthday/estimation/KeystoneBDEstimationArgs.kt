package co.electriccoin.zcash.ui.screen.keystonebirthday.estimation

import kotlinx.serialization.Serializable

@Serializable
data class KeystoneBDEstimationArgs(
    val ur: String,
    val accountIndex: Int,
    val blockHeight: Long
)
