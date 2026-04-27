package co.electriccoin.zcash.ui.screen.voting.signkeystone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import co.electriccoin.zcash.ui.common.usecase.CreateVotingKeystonePcztEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionBottomSheetState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.ZashiAccountInfoListItemState
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SignKeystoneVotingVM(
    private val args: SignKeystoneVotingArgs,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val createVotingKeystonePcztEncoder: CreateVotingKeystonePcztEncoderUseCase,
) : ViewModel() {
    private var signingBundle: VotingKeystoneSigningBundle? = null

    private val isBottomSheetVisible = MutableStateFlow(false)
    private val currentQrPart = MutableStateFlow<String?>(null)
    private val signingBundleState = MutableStateFlow<VotingKeystoneSigningBundle?>(null)

    val bottomSheetState =
        isBottomSheetVisible
            .map { isVisible ->
                if (isVisible) {
                    SignKeystoneTransactionBottomSheetState(
                        onBack = ::onCloseBottomSheetClick,
                        positiveButton =
                            ButtonState(
                                text = stringRes(R.string.sign_keystone_transaction_bottom_sheet_go_back),
                                onClick = ::onCloseBottomSheetClick
                            ),
                        negativeButton =
                            ButtonState(
                                text = stringRes(R.string.sign_keystone_transaction_bottom_sheet_reject),
                                onClick = ::onRejectBottomSheetClick
                            ),
                    )
                } else {
                    null
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                initialValue = null
            )

    val state: StateFlow<SignKeystoneTransactionState?> =
        combine(
            observeSelectedWalletAccount.require(),
            currentQrPart,
            signingBundleState
        ) { wallet, qrData, bundle ->
            bundle?.let {
                SignKeystoneTransactionState(
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = R.drawable.ic_settings_info,
                            title = wallet.name,
                            subtitle = stringRes("${it.roundTitle} - Bundle ${it.bundleIndex + 1}/${it.bundleCount}")
                        ),
                    generateNextQrCode = { currentQrPart.update { signingBundle?.encoder?.nextPart() } },
                    qrData = qrData,
                    positiveButton =
                        ButtonState(
                            text = stringRes(R.string.sign_keystone_transaction_positive),
                            onClick = ::onSignTransactionClick
                        ),
                    negativeButton =
                        ButtonState(
                            text = stringRes(R.string.sign_keystone_transaction_negative),
                            onClick = ::onRejectClick
                        ),
                    shareButton = null,
                    onBack = ::onBack,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            runCatching { createVotingKeystonePcztEncoder(args.roundIdHex) }
                .onSuccess { bundle ->
                    signingBundle = bundle
                    signingBundleState.value = bundle
                    currentQrPart.value = bundle.encoder.nextPart()
                }
        }
    }

    private fun onRejectBottomSheetClick() {
        viewModelScope.launch {
            isBottomSheetVisible.update { false }
            delay(350.milliseconds)
            navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
        }
    }

    private fun onCloseBottomSheetClick() {
        isBottomSheetVisible.update { false }
    }

    private fun onBack() {
        isBottomSheetVisible.update { !it }
    }

    private fun onRejectClick() {
        isBottomSheetVisible.update { true }
    }

    private fun onSignTransactionClick() {
        val bundle = signingBundle ?: return
        navigationRouter.forward(
            ScanKeystoneVotingPCZTRequest(
                roundIdHex = bundle.roundId,
                bundleIndex = bundle.bundleIndex,
                actionIndex = bundle.actionIndex
            )
        )
    }
}
