package co.electriccoin.zcash.ui.screen.connectkeystone

import co.electriccoin.zcash.ui.screen.connectkeystone.explainer.KeystoneHardwareWalletExplainerState

data class ConnectKeystoneState(
    val onViewKeystoneTutorialClicked: () -> Unit,
    val onBackClick: () -> Unit,
    val onContinueClick: () -> Unit,
    val explainer: KeystoneHardwareWalletExplainerState?,
)
