package piuk.blockchain.android.ui.dashboard.assetdetails

import com.blockchain.coincore.ActionState
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAsset
import com.blockchain.coincore.InterestAccount
import com.blockchain.coincore.StateAwareAction
import com.blockchain.coincore.selectFirstAccount
import com.blockchain.commonarch.presentation.mvi.MviModel
import com.blockchain.commonarch.presentation.mvi.MviState
import com.blockchain.core.price.HistoricalRateList
import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.core.price.Prices24HrWithDelta
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.extensions.minus
import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.RecurringBuy
import com.blockchain.remoteconfig.FeatureFlag
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.Singles
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.Stack
import timber.log.Timber

data class AssetDetailsState(
    val asset: CryptoAsset? = null,
    val selectedAccount: BlockchainAccount? = null,
    val actions: Set<StateAwareAction> = emptySet(),
    val assetDetailsCurrentStep: AssetDetailsStep = AssetDetailsStep.ZERO,
    val assetDisplayMap: AssetDisplayMap? = null,
    val recurringBuys: Map<String, RecurringBuy> = emptyMap(),
    val timeSpan: HistoricalTimeSpan = HistoricalTimeSpan.DAY,
    val chartLoading: Boolean = false,
    val chartData: HistoricalRateList = emptyList(),
    val errorState: AssetDetailsError = AssetDetailsError.NONE,
    val hostAction: AssetAction? = null,
    val selectedAccountCryptoBalance: Money? = null,
    val selectedAccountFiatBalance: Money? = null,
    val navigateToInterestDashboard: Boolean = false,
    val navigateToKyc: Boolean = false,
    val selectedRecurringBuy: RecurringBuy? = null,
    val paymentId: String? = null,
    val userBuyAccess: FeatureAccess = FeatureAccess.Unknown,
    val buySupported: Boolean = false,
    val stepsBackStack: Stack<AssetDetailsStep> = Stack(),
    val prices24HrWithDelta: Prices24HrWithDelta? = null
) : MviState

enum class AssetDetailsError {
    NONE,
    NO_CHART_DATA,
    NO_ASSET_DETAILS,
    TX_IN_FLIGHT,
    NO_RECURRING_BUYS,
    RECURRING_BUY_DELETE,
    NO_PRICE_DATA
}

class AssetDetailsModel(
    initialState: AssetDetailsState,
    mainScheduler: Scheduler,
    private val interactor: AssetDetailsInteractor,
    private val assetActionsComparator: Comparator<StateAwareAction>,
    private val entitySwitchSilverEligibilityFeatureFlag: FeatureFlag,
    environmentConfig: EnvironmentConfig,
    crashLogger: CrashLogger
) : MviModel<AssetDetailsState, AssetDetailsIntent>(
    initialState, mainScheduler, environmentConfig, crashLogger
) {
    override fun performAction(
        previousState: AssetDetailsState,
        intent: AssetDetailsIntent
    ): Disposable? {
        Timber.d("***> performAction: ${intent.javaClass.simpleName}")

        return when (intent) {
            is ShowRelevantAssetDetailsSheet -> interactor.shouldShowCustody(intent.asset)
                .subscribeBy(
                    onSuccess = {
                        if (it) {
                            process(ShowCustodyIntroSheetIntent)
                        } else {
                            process(ShowAssetDetailsIntent)
                        }
                    },
                    onError = {
                        // fail silently, try to show AssetSheet instead
                        process(ShowAssetDetailsIntent)
                    }
                )
            is ShowAssetActionsIntent -> accountActions(intent.account)
            is UpdateTimeSpan -> previousState.asset?.let { updateChartData(it, intent.updatedTimeSpan) }
            is CheckBuyStatus -> Singles.zip(
                interactor.userCanBuy(),
                previousState.asset?.let {
                    interactor.isAssetSupportedToBuy(it.assetInfo)
                } ?: Single.just(false)
            )
                .subscribeBy(
                    onSuccess = { (userFeatureAccess, supported) ->
                        process(UserBuyAccessUpdated(userFeatureAccess))
                        process(IsBuySupported(supported))
                    },
                    onError = {}
                )

            is LoadAsset -> {
                updateChartData(intent.asset, previousState.timeSpan)
                load24hPriceDelta(intent.asset)
                loadAssetDetails(intent.asset)
                loadRecurringBuysForAsset(intent.asset)
            }
            is DeleteRecurringBuy -> {
                previousState.selectedRecurringBuy?.let {
                    deleteRecurringBuy(it.id)
                }
            }
            is GetPaymentDetails -> {
                previousState.selectedRecurringBuy?.let {
                    loadPaymentDetails(it.paymentMethodType, it.paymentMethodId.orEmpty(), it.amount.currencyCode)
                }
            }
            is HandleActionIntent,
            is ChartLoading,
            is ChartDataLoaded,
            is ChartDataLoadFailed,
            is AssetDisplayDetailsLoaded,
            is AssetDisplayDetailsFailed,
            is ShowAssetDetailsIntent,
            is ShowCustodyIntroSheetIntent,
            is SelectAccount,
            is ReturnToPreviousStep,
            is ClearSheetDataIntent,
            is CustodialSheetFinished,
            is TransactionInFlight,
            is ShowInterestDashboard,
            is ClearActionStates,
            is AccountActionsLoaded,
            is RecurringBuyDataFailed,
            is RecurringBuyDataLoaded,
            is ShowRecurringBuySheet,
            is ClearSelectedRecurringBuy,
            is UpdateRecurringBuy,
            is UpdateRecurringBuyError,
            is UpdatePaymentDetails,
            is UpdatePriceDeltaDetails,
            is UserBuyAccessUpdated,
            is IsBuySupported,
            is HandleActionIntentLockedForTier,
            is NavigateToKyc,
            is UpdatePriceDeltaFailed -> null
        }
    }

    private fun load24hPriceDelta(token: CryptoAsset) =
        interactor.load24hPriceDelta(token.assetInfo)
            .subscribeBy(
                onSuccess = {
                    process(UpdatePriceDeltaDetails(it))
                },
                onError = {
                    process(UpdatePriceDeltaFailed)
                }
            )

    private fun loadPaymentDetails(
        paymentMethodType: PaymentMethodType,
        paymentMethodId: String,
        originCurrency: String
    ) =
        interactor.loadPaymentDetails(
            paymentMethodType,
            paymentMethodId,
            originCurrency
        )
            .subscribeBy(
                onSuccess = {
                    process(UpdatePaymentDetails(it))
                }
            )

    private fun deleteRecurringBuy(id: String) =
        interactor.deleteRecurringBuy(id)
            .subscribeBy(
                onComplete = {
                    process(UpdateRecurringBuy)
                },
                onError = {
                    process(UpdateRecurringBuyError)
                }
            )

    private fun loadAssetDetails(asset: CryptoAsset) =
        interactor.loadAssetDetails(asset)
            .subscribeBy(
                onSuccess = {
                    process(AssetDisplayDetailsLoaded(it))
                },
                onError = {
                    process(AssetDisplayDetailsFailed)
                }
            )

    private fun loadRecurringBuysForAsset(asset: CryptoAsset): Disposable =
        interactor.loadRecurringBuysForAsset(asset.assetInfo)
            .subscribeBy(
                onSuccess = { list ->
                    process(RecurringBuyDataLoaded(list.map { it.id to it }.toMap()))
                },
                onError = {
                    process(RecurringBuyDataFailed)
                }
            )

    private fun updateChartData(asset: CryptoAsset, timeSpan: HistoricalTimeSpan) =
        interactor.loadHistoricPrices(asset, timeSpan)
            .doOnSubscribe {
                process(ChartLoading)
            }.subscribeBy(
                onSuccess = {
                    process(ChartDataLoaded(it))
                },
                onError = {
                    process(ChartDataLoadFailed)
                }
            )

    private fun accountActions(account: BlockchainAccount): Disposable =
        entitySwitchSilverEligibilityFeatureFlag.enabled
            .onErrorReturnItem(false)
            .flatMap { enabled ->
                if (enabled) {
                    Singles.zip(account.stateAwareActions, account.isEnabled)
                } else {
                    Singles.zip(
                        account.actions.map { actions ->
                            actions.map { StateAwareAction(ActionState.Available, it) }.toSet()
                        },
                        account.isEnabled
                    )
                }
            }.subscribeBy(
                onSuccess = { (actions, enabled) ->
                    val sortedActions = when (account.selectFirstAccount()) {
                        is InterestAccount -> {
                            when {
                                !enabled && account.isFunded -> {
                                    actions.minus { it.action == AssetAction.InterestDeposit } +
                                        StateAwareAction(ActionState.Available, AssetAction.InterestWithdraw)
                                }
                                else -> {
                                    actions + StateAwareAction(ActionState.Available, AssetAction.InterestDeposit)
                                }
                            }
                        }
                        else -> actions.minus { it.action == AssetAction.InterestDeposit }
                    }.sortedWith(assetActionsComparator)
                    process(AccountActionsLoaded(account, sortedActions.toSet()))
                },
                onError = {
                    Timber.e("***> Error Loading account actions: $it")
                }
            )
}
