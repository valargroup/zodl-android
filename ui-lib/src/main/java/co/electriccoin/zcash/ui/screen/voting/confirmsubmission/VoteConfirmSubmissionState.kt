package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

sealed class VoteSubmissionStatus {
    data object Idle : VoteSubmissionStatus()

    data class Authorizing(val progress: Float) : VoteSubmissionStatus()

    data class Submitting(val current: Int, val total: Int, val progress: Float) : VoteSubmissionStatus()

    data object Completed : VoteSubmissionStatus()

    data class Failed(val error: String) : VoteSubmissionStatus()
}

data class VoteConfirmSubmissionState(
    val status: VoteSubmissionStatus,
    val roundTitle: StringResource,
    val votingWeightZEC: StringResource,
    val hotkeyAddress: StringResource,
    val isKeystoneUser: Boolean,
    val memo: StringResource,
    val ctaButton: ButtonState,
    val onBack: () -> Unit,
)
