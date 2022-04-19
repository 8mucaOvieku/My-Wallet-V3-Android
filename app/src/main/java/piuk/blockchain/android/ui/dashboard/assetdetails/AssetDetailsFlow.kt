package piuk.blockchain.android.ui.dashboard.assetdetails

import androidx.fragment.app.FragmentManager
import com.blockchain.analytics.Analytics
import com.blockchain.analytics.events.LaunchOrigin
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.AssetFilter
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.InterestAccount
import com.blockchain.coincore.NullCryptoAccount
import com.blockchain.coincore.SingleAccount
import com.blockchain.coincore.impl.CryptoAccountCustodialGroup
import com.blockchain.coincore.impl.CryptoAccountNonCustodialGroup
import com.blockchain.coincore.selectFirstAccount
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.FeatureAccess
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.account.AccountSelectSheet
import piuk.blockchain.android.ui.dashboard.sheets.KycUpgradeNowSheet
import piuk.blockchain.android.ui.dashboard.sheets.RecurringBuyDetailsSheet
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.analytics.SwapAnalyticsEvents
import timber.log.Timber

enum class AssetDetailsStep(val addToBackStack: Boolean = false) {
    ZERO,
    CUSTODY_INTRO_SHEET,
    ASSET_DETAILS(true),
    RECURRING_BUY_DETAILS(true),
    ASSET_ACTIONS(true),
    SELECT_ACCOUNT(true),
    KYC_UPGRADE_NOW(true)
}

class FullScreenCoinViewFlow(
    val asset: AssetInfo
) : DialogFlow() {
    override fun onSheetClosed() {
        // do nothing
    }
}

class AssetDetailsFlow(
    val asset: AssetInfo
) : DialogFlow(), KoinComponent, AccountSelectSheet.SelectAndBackHost, KycUpgradeNowSheet.Host {

    interface AssetDetailsHost : FlowHost {
        fun performAssetActionFor(action: AssetAction, account: BlockchainAccount)
        fun goToSellFrom(account: CryptoAccount) {}
        fun goToInterestDeposit(toAccount: InterestAccount) {}
        fun goToInterestWithdraw(fromAccount: InterestAccount) {}
        fun goToInterestDashboard() {}
        fun goToSummary(account: CryptoAccount) {}
        fun goToKyc()
        fun tryToLaunchBuy(asset: AssetInfo, buyAccess: FeatureAccess)
    }

    private var currentStep: AssetDetailsStep = AssetDetailsStep.ZERO
    private var localState: AssetDetailsState = AssetDetailsState()
    private val disposables = CompositeDisposable()
    private val model: AssetDetailsModel by scopedInject()
    private val coincore: Coincore by scopedInject()
    private val analytics: Analytics by inject()
    private lateinit var assetFlowHost: AssetDetailsHost

    override fun startFlow(fragmentManager: FragmentManager, host: FlowHost) {
        super.startFlow(fragmentManager, host)

        assetFlowHost = host as? AssetDetailsHost
            ?: throw IllegalStateException("Flow Host is not an AssetDetailsHost")

        model.apply {
            disposables += state.subscribeBy(
                onNext = { handleStateChange(it) },
                onError = { Timber.e("Asset details state is broken: $it") }
            )
        }

        model.process(ShowRelevantAssetDetailsSheet(asset))
    }

    private fun handleStateChange(
        newState: AssetDetailsState
    ) {

        if (currentStep != newState.assetDetailsCurrentStep) {
            currentStep = newState.assetDetailsCurrentStep
            if (currentStep == AssetDetailsStep.ZERO) {
                finishFlow()
            } else {
                showFlowStep(currentStep)
            }
        }

        if (newState.hostAction != null && localState.hostAction != newState.hostAction) {
            handleHostAction(newState, assetFlowHost)
        }

        if (newState.navigateToInterestDashboard) {
            assetFlowHost.goToInterestDashboard()
        }

        if (newState.navigateToKyc) {
            assetFlowHost.goToKyc()
        }

        localState = newState
    }

    private fun showFlowStep(step: AssetDetailsStep) {
        replaceBottomSheet(
            when (step) {
                AssetDetailsStep.ZERO -> null
                AssetDetailsStep.CUSTODY_INTRO_SHEET -> CustodyWalletIntroSheet.newInstance()
                AssetDetailsStep.ASSET_DETAILS -> AssetDetailSheet.newInstance(asset)
                AssetDetailsStep.ASSET_ACTIONS -> AssetActionsSheet.newInstance()
                AssetDetailsStep.SELECT_ACCOUNT -> AccountSelectSheet.newInstance(
                    this,
                    filterNonCustodialAccounts(localState.hostAction),
                    when (localState.hostAction) {
                        AssetAction.InterestDeposit -> R.string.select_interest_deposit_source_title
                        AssetAction.Send -> R.string.select_send_sheet_title
                        else -> R.string.select_account_sheet_title
                    }
                )
                AssetDetailsStep.RECURRING_BUY_DETAILS -> RecurringBuyDetailsSheet.newInstance()
                AssetDetailsStep.KYC_UPGRADE_NOW -> KycUpgradeNowSheet.newInstance(isHostAssetDetailsFlow = true)
            }
        )
    }

    private fun filterNonCustodialAccounts(
        action: AssetAction?
    ): Single<List<BlockchainAccount>> =
        coincore[asset].accountGroup(AssetFilter.NonCustodial)
            .map { it.accounts }.defaultIfEmpty(emptyList())
            .flattenAsObservable { it }
            .flatMapSingle { account ->
                account.actions.map { actions ->
                    if (
                        actions.contains(action) ||
                        (action == AssetAction.InterestDeposit && account.isFunded)
                    ) {
                        account
                    } else NullCryptoAccount()
                }
            }
            .filter { it !is NullCryptoAccount }
            .map { it as BlockchainAccount }
            .toList()

    private fun handleHostAction(
        newState: AssetDetailsState,
        host: AssetDetailsHost
    ) {
        when (newState.hostAction) {
            AssetAction.ViewActivity -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchActivity(it)
                    }
                )
            }
            AssetAction.Send -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchSend(it)
                    }
                )
            }
            AssetAction.Receive -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchReceive(it)
                    }
                )
            }
            AssetAction.Swap -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        launchSwap(it)
                    }
                )
            }
            AssetAction.Sell -> {
                selectAccountOrPerformAction(
                    state = newState,
                    singleAccountAction = {
                        host.goToSellFrom(it as CryptoAccount)
                        finishFlow()
                    }
                )
            }
            AssetAction.ViewStatement -> assetFlowHost.goToSummary(
                newState.selectedAccount.selectFirstAccount()
            )
            AssetAction.InterestDeposit -> {
                val account = newState.selectedAccount.selectFirstAccount()
                check(account is InterestAccount)
                assetFlowHost.goToInterestDeposit(
                    toAccount = account
                )
            }
            AssetAction.InterestWithdraw -> {
                val account = newState.selectedAccount.selectFirstAccount()
                check(account is InterestAccount)
                assetFlowHost.goToInterestWithdraw(
                    fromAccount = account
                )
            }
            AssetAction.Buy -> {
                newState.asset?.let {
                    assetFlowHost.tryToLaunchBuy(it.assetInfo, newState.userBuyAccess)
                    finishFlow()
                }
            }
            else -> throw IllegalStateException("${newState.hostAction} is not supported in this flow")
        }
    }

    private fun selectAccountOrPerformAction(
        state: AssetDetailsState,
        singleAccountAction: (SingleAccount) -> Unit
    ) {
        state.selectedAccount?.let {
            when (it) {
                is CryptoAccountCustodialGroup -> {
                    val firstAccount = it.accounts.first()
                    if (firstAccount is InterestAccount) {
                        if (state.hostAction == AssetAction.ViewActivity) {
                            singleAccountAction(firstAccount)
                        } else {
                            selectFromAccounts(state, singleAccountAction)
                        }
                    } else {
                        singleAccountAction(firstAccount)
                    }
                }
                is CryptoAccountNonCustodialGroup -> {
                    selectFromAccounts(state, singleAccountAction)
                }
                else -> throw IllegalStateException("Unsupported Account type $it")
            }
        }
    }

    private fun selectFromAccounts(
        state: AssetDetailsState,
        singleAccountAction: (SingleAccount) -> Unit
    ) {
        disposables += coincore[state.asset!!.assetInfo].accountGroup(AssetFilter.NonCustodial)
            .subscribeBy { ag ->
                when {
                    ag.accounts.size > 1 -> {
                        model.process(SelectAccount)
                    }
                    ag.accounts.size == 1 -> {
                        singleAccountAction(ag.accounts.first())
                    }
                    else -> throw IllegalStateException("Error when getting non-custodial accounts")
                }
            }
    }

    override fun finishFlow() {
        model.process(ClearSheetDataIntent)
        disposables.clear()
        currentStep = AssetDetailsStep.ZERO
        super.finishFlow()
    }

    override fun onAccountSelected(account: BlockchainAccount) {
        val singleAccount = account as SingleAccount
        when (localState.hostAction) {
            AssetAction.Send -> launchSend(singleAccount)
            AssetAction.Sell -> launchSell(singleAccount)
            AssetAction.ViewActivity -> launchActivity(singleAccount)
            AssetAction.Swap -> launchSwap(singleAccount)
            AssetAction.Receive -> launchReceive(singleAccount)
            else -> throw IllegalStateException(
                "Account selection not supported for this action ${localState.hostAction}"
            )
        }
    }

    private fun launchSell(singleAccount: SingleAccount) {
        (singleAccount as? CryptoAccount)?.let {
            assetFlowHost.goToSellFrom(it)
            finishFlow()
        }
    }

    private fun launchSend(account: SingleAccount) {
        assetFlowHost.performAssetActionFor(AssetAction.Send, account)
        finishFlow()
    }

    private fun launchReceive(account: SingleAccount) {
        assetFlowHost.performAssetActionFor(AssetAction.Receive, account)
        finishFlow()
    }

    private fun launchSwap(account: SingleAccount) {
        assetFlowHost.performAssetActionFor(AssetAction.Swap, account)
        finishFlow()
        analytics.logEvent(SwapAnalyticsEvents.SwapClickedEvent(LaunchOrigin.CURRENCY_PAGE))
    }

    private fun launchActivity(account: SingleAccount) {
        assetFlowHost.performAssetActionFor(AssetAction.ViewActivity, account)
        finishFlow()
    }

    override fun onAccountSelectorBack() {
        model.process(ReturnToPreviousStep)
    }

    override fun startKycClicked() {
        assetFlowHost.goToKyc()
        finishFlow()
    }

    override fun onSheetClosed() {
        finishFlow()
    }
}
