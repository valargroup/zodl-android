package co.electriccoin.zcash.ui.screen.transactionprogress

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

@Immutable
data class TransactionProgressState(
    val background: Background?,
    val image: ImageResource,
    val title: StringResource,
    val subtitle: StyledStringResource,
    val middleButton: ButtonState?,
    val primaryButton: ButtonState?,
    val secondaryButton: ButtonState?,
    val onBack: () -> Unit,
    val transactionIds: List<StringResource>? = null,
    val showAppBar: Boolean = false,
) {
    enum class Background { SUCCESS, PENDING, ERROR }
}
