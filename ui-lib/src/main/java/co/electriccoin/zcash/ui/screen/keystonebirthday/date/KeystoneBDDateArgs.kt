package co.electriccoin.zcash.ui.screen.keystonebirthday.date

import kotlinx.serialization.Serializable

@Serializable
data class KeystoneBDDateArgs(
    val ur: String,
    val accountIndex: Int
)
