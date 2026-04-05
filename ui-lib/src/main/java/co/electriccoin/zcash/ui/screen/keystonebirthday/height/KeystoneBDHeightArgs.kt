package co.electriccoin.zcash.ui.screen.keystonebirthday.height

import kotlinx.serialization.Serializable

@Serializable
data class KeystoneBDHeightArgs(
    val ur: String,
    val accountIndex: Int
)
