package piuk.blockchain.android.ui.home.models

import android.content.Intent
import com.blockchain.analytics.events.LaunchOrigin
import com.blockchain.api.NabuApiException
import com.blockchain.banking.BankPaymentApproval
import com.blockchain.coincore.AssetAction
import com.blockchain.commonarch.presentation.mvi.MviModel
import com.blockchain.domain.paymentmethods.model.BankTransferDetails
import com.blockchain.domain.paymentmethods.model.BankTransferStatus
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.extensions.enumValueOfOrNull
import com.blockchain.extensions.exhaustive
import com.blockchain.logging.RemoteLogger
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.models.responses.nabu.CampaignData
import com.blockchain.network.PollResult
import com.blockchain.utils.capitalizeFirstChar
import com.blockchain.walletconnect.domain.WalletConnectServiceAPI
import com.blockchain.walletconnect.domain.WalletConnectSessionEvent
import com.google.gson.JsonSyntaxException
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.deeplink.BlockchainLinkState
import piuk.blockchain.android.deeplink.LinkState
import piuk.blockchain.android.deeplink.OpenBankingLinkType
import piuk.blockchain.android.kyc.KycLinkState
import piuk.blockchain.android.scan.QrScanError
import piuk.blockchain.android.scan.ScanResult
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.ui.linkbank.BankAuthDeepLinkState
import piuk.blockchain.android.ui.linkbank.BankAuthFlowState
import piuk.blockchain.android.ui.sell.BuySellFragment
import piuk.blockchain.android.ui.upsell.KycUpgradePromptManager
import piuk.blockchain.androidcore.utils.extensions.emptySubscribe
import timber.log.Timber

class MainModel(
    initialState: MainState,
    mainScheduler: Scheduler,
    private val interactor: MainInteractor,
    private val walletConnectServiceAPI: WalletConnectServiceAPI,
    environmentConfig: EnvironmentConfig,
    remoteLogger: RemoteLogger
) : MviModel<MainState, MainIntent>(
    initialState,
    mainScheduler,
    environmentConfig,
    remoteLogger,
) {

    private val compositeDisposable = CompositeDisposable()

    fun clearDisposables() = compositeDisposable.clear()

    init {
        compositeDisposable += walletConnectServiceAPI.sessionEvents.subscribeBy { sessionEvent ->
            when (sessionEvent) {
                is WalletConnectSessionEvent.ReadyForApproval -> process(
                    MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchWalletConnectSessionApproval(sessionEvent.session))
                )
                is WalletConnectSessionEvent.DidConnect -> process(
                    MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchWalletConnectSessionApproved(sessionEvent.session))
                )
                is WalletConnectSessionEvent.FailToConnect,
                is WalletConnectSessionEvent.DidReject -> process(
                    MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchWalletConnectSessionRejected(sessionEvent.session))
                )
                is WalletConnectSessionEvent.DidDisconnect -> {
                    Timber.i("Session ${sessionEvent.session.url} Disconnected")
                }
            }.exhaustive
        }
    }

    override fun performAction(previousState: MainState, intent: MainIntent): Disposable? =
        when (intent) {
            is MainIntent.PerformInitialChecks -> {
                interactor.checkForUserWalletErrors()
                    .subscribeBy(
                        onComplete = {
                            if (previousState.deeplinkIntent != null) {
                                previousState.deeplinkIntent.data?.let { uri ->
                                    interactor.processDeepLinkV2(uri).subscribeBy(
                                        onComplete = {
                                            // Nothing to do. Deeplink V2 was parsed successfully
                                        },
                                        onError = {
                                            // Deeplink V2 parsing failed, fallback to legacy
                                            Timber.e(it)
                                            process(MainIntent.CheckForPendingLinks(previousState.deeplinkIntent))
                                        }
                                    )
                                }
                            }
                        },
                        onError = { throwable ->
                            if (throwable is NabuApiException && throwable.isUserWalletLinkError()) {
                                process(
                                    MainIntent.UpdateViewToLaunch(
                                        ViewToLaunch.CheckForAccountWalletLinkErrors(throwable.getWalletIdHint())
                                    )
                                )
                            }
                        }
                    )
            }
            is MainIntent.CheckForInitialDialogs -> if (intent.shouldStartUiTour) {
                process(MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowUiTour))
                null
            } else {
                interactor.shouldShowEntitySwitchSilverKycUpsell()
                    .onErrorReturnItem(false)
                    .subscribeBy { show ->
                        if (show) process(MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowEntitySwitchSilverKycUpsell))
                    }
            }
            is MainIntent.CheckForPendingLinks -> {
                interactor.checkForDeepLinks(intent.appIntent)
                    .subscribeBy(
                        onSuccess = { linkState ->
                            if ((
                                intent.appIntent.flags
                                    and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                                ) == 0
                            ) {
                                dispatchDeepLink(linkState)
                            }
                        },
                        onError = { Timber.e(it) }
                    )
            }
            is MainIntent.ClearDeepLinkResult -> interactor.clearDeepLink()
                .onErrorComplete()
                .subscribe()
            is MainIntent.ValidateAccountAction ->
                interactor.checkIfShouldUpsell(intent.action, intent.account)
                    .subscribeBy(
                        onSuccess = { upsell ->
                            if (upsell != KycUpgradePromptManager.Type.NONE) {
                                process(
                                    MainIntent.UpdateViewToLaunch(
                                        ViewToLaunch.LaunchUpsellAssetAction(upsell)
                                    )
                                )
                            } else {
                                process(
                                    MainIntent.UpdateViewToLaunch(
                                        ViewToLaunch.LaunchAssetAction(intent.action, intent.account)
                                    )
                                )
                            }
                        },
                        onError = {
                            Timber.e("Upsell manager failure")
                        }
                    )
            is MainIntent.UnpairWallet -> interactor.unpairWallet()
                .onErrorComplete()
                .subscribe()
            is MainIntent.CancelAnyPendingConfirmationBuy -> interactor.cancelAnyPendingConfirmationBuy()
                .subscribe()
            is MainIntent.ProcessScanResult -> interactor.processQrScanResult(intent.decodedData)
                .subscribeBy(
                    onSuccess = {
                        when (it) {
                            is ScanResult.HttpUri -> handlePossibleDeepLinkFromScan(it)
                            is ScanResult.TxTarget -> {
                                process(
                                    MainIntent.UpdateViewToLaunch(
                                        ViewToLaunch.LaunchTransactionFlowWithTargets(it.targets)
                                    )
                                )
                            }
                            is ScanResult.WalletConnectRequest -> walletConnectServiceAPI.attemptToConnect(it.data)
                                .emptySubscribe()
                            is ScanResult.ImportedWallet -> {
                                // TODO: as part of Auth
                            }
                            is ScanResult.SecuredChannelLogin -> interactor.sendSecureChannelHandshake(it.handshake)
                        }
                    },
                    onError = {
                        when (it) {
                            is QrScanError -> process(
                                MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowTargetScanError(it))
                            )
                            else -> {
                                Timber.d("Scan failed")
                            }
                        }
                    }
                )
            is MainIntent.ApproveWCSession -> walletConnectServiceAPI.acceptConnection(intent.session).emptySubscribe()
            is MainIntent.RejectWCSession -> walletConnectServiceAPI.denyConnection(intent.session).emptySubscribe()
            MainIntent.ResetViewState,
            is MainIntent.UpdateViewToLaunch -> null
            is MainIntent.UpdateDeepLinkResult -> null
            is MainIntent.SaveDeeplinkIntent -> null
        }

    private fun handlePossibleDeepLinkFromScan(scanResult: ScanResult.HttpUri) {
        compositeDisposable += interactor.checkForDeepLinks(scanResult)
            .subscribeBy(
                onSuccess = {
                    dispatchDeepLink(it)
                },
                onError = { Timber.e(it) }
            )
    }

    private fun dispatchDeepLink(linkState: LinkState) {
        when (linkState) {
            is LinkState.EmailVerifiedDeepLink -> {
                // no-op - keeping the event for email verification
            }
            is LinkState.KycDeepLink -> handleKycDeepLink(linkState)
            is LinkState.OpenBankingLink -> handleOpenBankingDeepLink(linkState)
            is LinkState.BlockchainLink -> handleBlockchainDeepLink(linkState)
            else -> {
                // do nothing
            }
        }
    }

    private fun handleBlockchainDeepLink(linkState: LinkState.BlockchainLink) {
        when (val link = linkState.link) {
            BlockchainLinkState.NoUri -> Timber.e("Invalid deep link")
            BlockchainLinkState.Swap -> process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchSwap))
            BlockchainLinkState.TwoFa -> process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchTwoFaSetup))
            BlockchainLinkState.VerifyEmail -> process(
                MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchVerifyEmail)
            )
            BlockchainLinkState.SetupFingerprint -> process(
                MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchSetupBiometricLogin)
            )
            BlockchainLinkState.Interest -> process(
                MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchInterestDashboard(LaunchOrigin.DEEPLINK))
            )
            BlockchainLinkState.Receive -> process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchReceive))
            BlockchainLinkState.Send -> process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchSend))
            is BlockchainLinkState.Sell -> process(
                MainIntent.UpdateViewToLaunch(
                    ViewToLaunch.LaunchBuySell(
                        BuySellFragment.BuySellViewType.TYPE_SELL,
                        interactor.getAssetFromTicker(link.ticker)
                    )
                )
            )
            is BlockchainLinkState.Activities -> process(
                MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchAssetAction(AssetAction.ViewActivity, null))
            )
            is BlockchainLinkState.Buy -> process(
                MainIntent.UpdateViewToLaunch(
                    ViewToLaunch.LaunchBuySell(
                        BuySellFragment.BuySellViewType.TYPE_BUY,
                        interactor.getAssetFromTicker(link.ticker)
                    )
                )
            )
            is BlockchainLinkState.SimpleBuy -> process(
                MainIntent.UpdateViewToLaunch(
                    ViewToLaunch.LaunchSimpleBuy(
                        interactor.getAssetFromTicker(link.ticker) ?: throw IllegalStateException(
                            "Unknown asset ticker ${link.ticker}"
                        )
                    )
                )
            )
            is BlockchainLinkState.KycCampaign ->
                process(
                    MainIntent.UpdateViewToLaunch(
                        ViewToLaunch.LaunchKyc(
                            enumValueOfOrNull<CampaignType>(
                                link.campaignType.capitalizeFirstChar()
                            ) ?: CampaignType.None
                        )
                    )
                )
        }
    }

    private fun handleKycDeepLink(linkState: LinkState.KycDeepLink) {
        when (linkState.link) {
            is KycLinkState.Resubmit -> process(
                MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchKyc(CampaignType.Resubmission))
            )
            is KycLinkState.EmailVerified -> process(
                MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchKyc(CampaignType.None))
            )
            is KycLinkState.General -> {
                val data = linkState.link.campaignData
                if (data != null) {
                    registerForCampaign(data)
                } else {
                    process(
                        MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchKyc(CampaignType.None))
                    )
                }
            }
            else -> {
                // do nothing
            }
        }
    }

    private fun registerForCampaign(campaignData: CampaignData) {
        // Future campaigns register here
    }

    private fun handleOpenBankingDeepLink(state: LinkState.OpenBankingLink) =
        when (state.type) {
            OpenBankingLinkType.LINK_BANK -> handleBankLinking(state.consentToken)
            OpenBankingLinkType.PAYMENT_APPROVAL -> handleBankApproval(state.consentToken)
            OpenBankingLinkType.UNKNOWN -> process(MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowOpenBankingError))
        }

    private fun handleBankLinking(consentToken: String?) {
        val bankLinkingState = interactor.getBankLinkingState()

        if (bankLinkingState.bankAuthFlow == BankAuthFlowState.BANK_LINK_COMPLETE) {
            interactor.resetLocalBankAuthState()
            return
        }

        consentToken?.let { token ->
            compositeDisposable += interactor.updateOpenBankingConsent(token)
                .subscribeBy(
                    onComplete = {
                        try {
                            interactor.updateBankLinkingState(
                                bankLinkingState.copy(bankAuthFlow = BankAuthFlowState.BANK_LINK_COMPLETE)
                            )

                            bankLinkingState.bankLinkingInfo?.let {
                                process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingLinking(it)))
                            }
                        } catch (e: JsonSyntaxException) {
                            process(MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowOpenBankingError))
                        }
                    },
                    onError = {
                        Timber.e("Error updating consent token on new bank link: $it")
                        bankLinkingState.bankLinkingInfo?.let { linkingInfo ->
                            process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingLinking(linkingInfo)))
                        } ?: process(MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowOpenBankingError))
                    }
                )
        } ?: run {
            Timber.e("Error updating consent token on new bank link: token is null.")
            bankLinkingState.bankLinkingInfo?.let { linkingInfo ->
                process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingLinking(linkingInfo)))
            } ?: process(MainIntent.UpdateViewToLaunch(ViewToLaunch.ShowOpenBankingError))
        }
    }

    private fun handleBankApproval(consentToken: String?) {
        val deepLinkState = interactor.getBankLinkingState()

        if (deepLinkState.bankAuthFlow == BankAuthFlowState.BANK_APPROVAL_COMPLETE) {
            interactor.resetLocalBankAuthState()
            return
        }

        consentToken?.let { token ->
            compositeDisposable += interactor.updateOpenBankingConsent(token)
                .subscribeBy(
                    onComplete = {
                        if (deepLinkState.bankAuthFlow == BankAuthFlowState.BANK_APPROVAL_PENDING) {
                            deepLinkState.bankPaymentData?.let { paymentData ->
                                handleDepositApproval(paymentData, deepLinkState)
                            } ?: handleSimpleBuyApproval()
                        }
                    },
                    onError = {
                        Timber.e("Error updating consent token on approval: $it")

                        interactor.resetLocalBankAuthState()

                        deepLinkState.bankPaymentData?.let { data ->
                            process(
                                MainIntent.UpdateViewToLaunch(
                                    ViewToLaunch.LaunchOpenBankingError(data.orderValue.currencyCode)
                                )
                            )
                        } ?: process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingBuyApprovalError))
                    }
                )
        } ?: run {
            deepLinkState.bankPaymentData?.let {
                compositeDisposable += interactor.cancelOrder(it.paymentId).subscribeBy(
                    onComplete = {
                        process(
                            MainIntent.UpdateViewToLaunch(
                                ViewToLaunch.LaunchOpenBankingError(it.orderValue.currencyCode)
                            )
                        )
                    }
                )
            } ?: run {
                process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingBuyApprovalError))
            }
        }
    }

    private fun handleDepositApproval(
        paymentData: BankPaymentApproval,
        deepLinkState: BankAuthDeepLinkState
    ) {
        compositeDisposable += interactor.pollForBankTransferCharge(paymentData)
            .doOnSubscribe {
                process(
                    MainIntent.UpdateViewToLaunch(
                        ViewToLaunch.LaunchOpenBankingApprovalDepositInProgress(paymentData.orderValue)
                    )
                )
            }.subscribeBy(
                onSuccess = {
                    when (it) {
                        is PollResult.FinalResult -> {
                            interactor.updateBankLinkingState(
                                deepLinkState.copy(
                                    bankAuthFlow = BankAuthFlowState.BANK_APPROVAL_COMPLETE,
                                    bankPaymentData = null,
                                    bankLinkingInfo = null
                                )
                            )

                            handleTransferStatus(it.value, paymentData)
                        }
                        is PollResult.TimeOut -> {
                            process(
                                MainIntent.UpdateViewToLaunch(
                                    ViewToLaunch.LaunchOpenBankingApprovalTimeout(paymentData.orderValue.currencyCode)
                                )
                            )
                            interactor.updateBankLinkingState(
                                deepLinkState.copy(
                                    bankAuthFlow = BankAuthFlowState.BANK_APPROVAL_COMPLETE,
                                    bankPaymentData = null,
                                    bankLinkingInfo = null
                                )
                            )
                        }
                        is PollResult.Cancel -> {
                            // do nothing
                        }
                        else -> {
                            // do nothing
                        }
                    }
                },
                onError = { error ->
                    interactor.resetLocalBankAuthState()
                    (error as? NabuApiException)?.getServerSideErrorInfo()?.let { info ->
                        process(
                            MainIntent.UpdateViewToLaunch(
                                ViewToLaunch.LaunchServerDrivenOpenBankingError(
                                    currencyCode = paymentData.orderValue.currencyCode,
                                    title = info.title,
                                    description = info.description
                                )
                            )
                        )
                    } ?: process(
                        MainIntent.UpdateViewToLaunch(
                            ViewToLaunch.LaunchOpenBankingError(paymentData.orderValue.currencyCode)
                        )
                    )
                }
            )
    }

    private fun handleTransferStatus(
        it: BankTransferDetails,
        paymentData: BankPaymentApproval
    ) {
        when (it.status) {
            BankTransferStatus.COMPLETE -> {
                process(
                    MainIntent.UpdateViewToLaunch(
                        ViewToLaunch.LaunchOpenBankingApprovalDepositComplete(
                            it.amount, interactor.getEstimatedDepositCompletionTime()
                        )
                    )
                )
            }
            BankTransferStatus.PENDING -> {
                process(
                    MainIntent.UpdateViewToLaunch(
                        ViewToLaunch.LaunchOpenBankingApprovalTimeout(paymentData.orderValue.currencyCode)
                    )
                )
            }
            BankTransferStatus.ERROR,
            BankTransferStatus.UNKNOWN -> {
                process(
                    MainIntent.UpdateViewToLaunch(
                        ViewToLaunch.LaunchOpenBankingError(paymentData.orderValue.currencyCode)
                    )
                )
            }
        }
    }

    private fun handleSimpleBuyApproval() {
        interactor.getSimpleBuySyncLocalState()?.let {
            handleOrderState(it)
        } ?: kotlin.run {
            // try to sync with server once, otherwise fail
            compositeDisposable += interactor.performSimpleBuySync()
                .subscribeBy(
                    onComplete = {
                        interactor.getSimpleBuySyncLocalState()?.let {
                            handleOrderState(it)
                        } ?: process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingBuyApprovalError))
                    }, onError = {
                    Timber.e("Error doing SB sync for bank linking $it")
                    interactor.resetLocalBankAuthState()
                    process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchOpenBankingBuyApprovalError))
                }
                )
        }
    }

    private fun handleOrderState(state: SimpleBuyState) {
        if (state.orderState == OrderState.AWAITING_FUNDS) {
            process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchSimpleBuyFromDeepLinkApproval))
        } else {
            interactor.resetLocalBankAuthState()
            process(MainIntent.UpdateViewToLaunch(ViewToLaunch.LaunchPaymentForCancelledOrder(state)))
        }
    }

    private fun NabuApiException.getWalletIdHint(): String =
        getErrorDescription().split(NabuApiException.USER_WALLET_LINK_ERROR_PREFIX).last()
}
