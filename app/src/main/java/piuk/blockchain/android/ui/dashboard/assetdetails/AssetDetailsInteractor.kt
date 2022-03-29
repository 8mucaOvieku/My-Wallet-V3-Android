package piuk.blockchain.android.ui.dashboard.assetdetails

import com.blockchain.coincore.AccountGroup
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.AssetFilter
import com.blockchain.coincore.AvailableActions
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.CryptoAsset
import com.blockchain.core.payments.PaymentsDataManager
import com.blockchain.core.price.ExchangeRate
import com.blockchain.core.price.HistoricalRateList
import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.nabu.Feature
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.FundsAccount
import com.blockchain.nabu.models.data.RecurringBuy
import com.blockchain.nabu.models.data.RecurringBuyPaymentDetails
import com.blockchain.preferences.DashboardPrefs
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import timber.log.Timber

typealias AssetDisplayMap = Map<AssetFilter, AssetDisplayInfo>

sealed class AssetDetailsItem {
    data class CryptoDetailsInfo(
        val assetFilter: AssetFilter,
        val account: BlockchainAccount,
        val balance: Money,
        val fiatBalance: Money,
        val actions: Set<AssetAction>,
        val interestRate: Double = Double.NaN
    ) : AssetDetailsItem()

    data class RecurringBuyInfo(
        val recurringBuy: RecurringBuy
    ) : AssetDetailsItem()
}

data class AssetDisplayInfo(
    val account: BlockchainAccount,
    val amount: Money,
    val pendingAmount: Money,
    val fiatValue: Money,
    val actions: Set<AssetAction>,
    val interestRate: Double = Double.NaN
)

class AssetDetailsInteractor(
    private val dashboardPrefs: DashboardPrefs,
    private val coincore: Coincore,
    private val userIdentity: UserIdentity,
    private val custodialWalletManager: CustodialWalletManager,
    private val paymentsDataManager: PaymentsDataManager
) {

    fun loadAssetDetails(asset: CryptoAsset) =
        getAssetDisplayDetails(asset)

    fun loadHistoricPrices(asset: CryptoAsset, timeSpan: HistoricalTimeSpan): Single<HistoricalRateList> =
        asset.historicRateSeries(timeSpan)
            .onErrorResumeNext { Single.just(emptyList()) }

    fun shouldShowCustody(asset: AssetInfo): Single<Boolean> {
        return coincore[asset].accountGroup(AssetFilter.Custodial)
            .flatMapSingle { it.balance.firstOrError() }
            .map {
                !dashboardPrefs.isCustodialIntroSeen && !it.total.isZero
            }.defaultIfEmpty(false)
    }

    fun load24hPriceDelta(asset: AssetInfo) =
        coincore[asset].getPricesWith24hDelta()

    fun loadPaymentDetails(
        paymentMethodType: PaymentMethodType,
        paymentMethodId: String,
        originCurrency: String
    ): Single<RecurringBuyPaymentDetails> {
        return when (paymentMethodType) {
            PaymentMethodType.PAYMENT_CARD -> paymentsDataManager.getCardDetails(paymentMethodId)
                .map { it }
            PaymentMethodType.BANK_TRANSFER -> paymentsDataManager.getLinkedBank(paymentMethodId)
                .map { it.toPaymentMethod() }
            PaymentMethodType.FUNDS -> Single.just(FundsAccount(currency = originCurrency))

            else -> Single.just(object : RecurringBuyPaymentDetails {
                override val paymentDetails: PaymentMethodType
                    get() = paymentMethodType
            })
        }
    }

    fun deleteRecurringBuy(id: String) = custodialWalletManager.cancelRecurringBuy(id)

    private sealed class Details {
        object NoDetails : Details()
        class DetailsItem(
            val isEnabled: Boolean,
            val account: BlockchainAccount,
            val balance: Money,
            val pendingBalance: Money,
            val actions: AvailableActions
        ) : Details()
    }

    private fun Maybe<AccountGroup>.mapDetails(): Single<Details> =
        this.flatMap { grp ->
            Single.zip(
                grp.balance.firstOrError(),
                grp.isEnabled,
                grp.actions
            ) { balance, enable, actions ->
                Details.DetailsItem(
                    isEnabled = enable,
                    account = grp,
                    balance = balance.total,
                    pendingBalance = balance.pending,
                    actions = actions
                ) as Details
            }.toMaybe()
        }.defaultIfEmpty(Details.NoDetails)

    private fun getAssetDisplayDetails(asset: CryptoAsset): Single<AssetDisplayMap> {
        return Single.zip(
            asset.getPricesWith24hDelta(),
            asset.accountGroup(AssetFilter.NonCustodial).mapDetails(),
            asset.accountGroup(AssetFilter.Custodial).mapDetails(),
            asset.accountGroup(AssetFilter.Rewards).mapDetails(),
            asset.interestRate()
        ) { prices, nonCustodial, custodial, interest, interestRate ->
            makeAssetDisplayMap(
                prices.currentRate, nonCustodial, custodial, interest, interestRate
            )
        }.doOnError {
            Timber.e("Unable to load asset details. Why? $it")
        }
    }

    private fun makeAssetDisplayMap(
        fiatRate: ExchangeRate,
        nonCustodial: Details,
        custodial: Details,
        interest: Details,
        interestRate: Double
    ): AssetDisplayMap = mutableMapOf<AssetFilter, AssetDisplayInfo>().apply {
        if (nonCustodial !is Details.NoDetails) {
            addToDisplayMap(this, AssetFilter.NonCustodial, nonCustodial, fiatRate)
        }

        if (custodial !is Details.NoDetails) {
            addToDisplayMap(this, AssetFilter.Custodial, custodial, fiatRate)
        }

        (interest as? Details.DetailsItem)?.let { item ->
            if (item.isEnabled || item.balance.isPositive) {
                addToDisplayMap(this, AssetFilter.Rewards, interest, fiatRate, interestRate)
            }
        }
    }

    private fun addToDisplayMap(
        map: MutableMap<AssetFilter, AssetDisplayInfo>,
        filter: AssetFilter,
        item: Details,
        fiatRate: ExchangeRate,
        interestRate: Double = Double.NaN
    ) {
        (item as? Details.DetailsItem)?.let {
            val fiat = fiatRate.convert(it.balance)
            map.put(
                filter,
                AssetDisplayInfo(
                    account = it.account,
                    amount = it.balance,
                    fiatValue = fiat,
                    pendingAmount = it.pendingBalance,
                    actions = it.actions.filter { action ->
                        action != AssetAction.InterestDeposit
                    }.toSet(),
                    interestRate = interestRate
                )
            )
        }
    }

    fun loadRecurringBuysForAsset(assetTicker: AssetInfo) =
        custodialWalletManager.getRecurringBuysForAsset(assetTicker)

    fun userCanBuy(): Single<FeatureAccess> =
        userIdentity.userAccessForFeature(Feature.SimpleBuy)

    fun isAssetSupportedToBuy(assetInfo: AssetInfo): Single<Boolean> =
        custodialWalletManager.isCurrencyAvailableForTrading(assetInfo)
}
