package co.electriccoin.zcash.ui.screen.voting.signkeystone

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.CreateVotingKeystonePcztEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.addressbook.ADDRESS_MAX_LENGTH
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
    private val votingRecoveryRepository: VotingRecoveryRepository,
) : ViewModel() {
    private var signingBundle: VotingKeystoneSigningBundle? = null
    private val selectedAccountUuid =
        observeSelectedWalletAccount.require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private val isLoading = MutableStateFlow(true)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isBottomSheetVisible = MutableStateFlow(false)
    private val currentQrPart = MutableStateFlow<String?>(null)
    private val signingBundleState = MutableStateFlow<VotingKeystoneSigningBundle?>(null)

    val loading: StateFlow<Boolean> = isLoading
    val error: StateFlow<String?> = errorMessage

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
                    barTitle = stringRes("Confirmation"),
                    title = stringRes("Scan with your Keystone wallet"),
                    subtitle = stringRes(
                        "After you have signed with Keystone, tap on the Scan Signature button below."
                    ),
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = wallet.icon,
                            title = wallet.name,
                            subtitle = stringRes("${wallet.unified.address.address.take(ADDRESS_MAX_LENGTH)}...")
                        ),
                    badgeText = stringRes("Hardware"),
                    generateNextQrCode = { currentQrPart.update { signingBundle?.encoder?.nextPart() } },
                    qrData = qrData,
                    positiveButton =
                        ButtonState(
                            text = stringRes("Scan Signature"),
                            onClick = ::onSignTransactionClick
                        ),
                    negativeButton =
                        ButtonState(
                            text = stringRes("Cancel"),
                            onClick = ::onCancelClick
                        ),
                    shareButton = null,
                    onBack = ::onCancelClick,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            initialValue = null
        )

    init {
        loadSigningBundle()
    }

    private fun onRejectBottomSheetClick() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            isBottomSheetVisible.update { false }
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = args.roundIdHex,
                routeStage = VotingKeystoneRouteStage.SIGN
            )
            delay(350.milliseconds)
            navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
        }
    }

    private fun onCloseBottomSheetClick() {
        isBottomSheetVisible.update { false }
    }

    fun onScreenBack() {
        navigationRouter.back()
    }

    fun onRetry() {
        if (isLoading.value) {
            return
        }
        loadSigningBundle()
    }

    private fun onCancelClick() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = args.roundIdHex,
                routeStage = VotingKeystoneRouteStage.SIGN
            )
            navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
        }
    }

    private fun onSignTransactionClick() {
        val bundle = signingBundle ?: return
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = bundle.roundId,
                routeStage = VotingKeystoneRouteStage.SCAN
            )
            navigationRouter.forward(
                ScanKeystoneVotingPCZTRequest(
                    roundIdHex = bundle.roundId,
                    bundleIndex = bundle.bundleIndex,
                    actionIndex = bundle.actionIndex
                )
            )
        }
    }

    private fun loadSigningBundle() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value
            if (accountUuid == null) {
                errorMessage.value = "No selected Keystone account is available."
                isLoading.value = false
                return@launch
            }
            isLoading.value = true
            errorMessage.value = null
            currentQrPart.value = null
            signingBundle = null
            signingBundleState.value = null
            runCatching { createVotingKeystonePcztEncoder(accountUuid, args.roundIdHex) }
                .onSuccess { bundle ->
                    signingBundle = bundle
                    signingBundleState.value = bundle
                    currentQrPart.value = bundle.encoder.nextPart()
                }.onFailure { throwable ->
                    Log.e(
                        "SignKeystoneVoting",
                        "Failed to create Keystone voting QR bundle for ${args.roundIdHex}",
                        throwable
                    )
                    errorMessage.value =
                        throwable.message ?: "Failed to prepare the Keystone signing request."
                }
            isLoading.value = false
        }
    }
}
