package com.blockchain.home.presentation.allassets

import androidx.lifecycle.viewModelScope
import com.blockchain.coincore.AccountBalance
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.SingleAccount
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.MviViewModel
import com.blockchain.componentlib.tablerow.ValueChange
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.core.price.Prices24HrWithDelta
import com.blockchain.data.DataResource
import com.blockchain.data.map
import com.blockchain.data.updateDataWith
import com.blockchain.extensions.replace
import com.blockchain.home.domain.HomeAccountsService
import com.blockchain.home.model.AssetFilter
import com.blockchain.home.model.AssetFilterStatus
import com.blockchain.home.presentation.dashboard.HomeNavEvent
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.MultiAppAssetsFilterService
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class AssetsViewModel(
    private val homeAccountsService: HomeAccountsService,
    private val currencyPrefs: CurrencyPrefs,
    private val exchangeRates: ExchangeRatesDataManager,
    private val filterService: MultiAppAssetsFilterService
) : MviViewModel<AssetsIntent, AssetsViewState, AssetsModelState, HomeNavEvent, ModelConfigArgs.NoArgs>(
    AssetsModelState()
) {

    private var accountsJob: Job? = null

    override fun viewCreated(args: ModelConfigArgs.NoArgs) {
        updateState { state ->
            state.copy(accounts = DataResource.Data(emptyList()))
        }
    }

    override fun reduce(state: AssetsModelState): AssetsViewState {
        return with(state) {
            AssetsViewState(
                balance = accounts.totalBalance(),
                cryptoAssets = state.accounts.map { modelAccounts ->
                    modelAccounts
                        .filter { modelAccount -> modelAccount.singleAccount is CryptoAccount }
                        .filter { modelAccount ->
                            // create search term filter predicate
                            val searchTermPredicate = if (state.filterTerm.isEmpty()) {
                                true
                            } else {
                                with(modelAccount.singleAccount.currency) {
                                    displayTicker.contains(state.filterTerm, ignoreCase = true) ||
                                        name.contains(state.filterTerm, ignoreCase = true)
                                }
                            }

                            // create predicate for all filters
                            val filtersPredicate = filters.map { assetFilter ->
                                when (assetFilter.filter) {
                                    AssetFilter.ShowSmallBalances -> {
                                        if (assetFilter.isEnabled) {
                                            // auto pass check
                                            true
                                        } else {
                                            // filter out small balances
                                            fun Money.isHighBalance(): Boolean {
                                                return this >= Money.fromMajor(
                                                    currency,
                                                    AssetFilter.ShowSmallBalances.MinimumBalance
                                                )
                                            }

                                            (modelAccount.usdBalance.map { it.isHighBalance() } as? DataResource.Data)
                                                ?.data.let { isHighBalance ->
                                                    // if null (e.g. loading), or true -> pass
                                                    isHighBalance != false
                                                }
                                        }
                                    }
                                }
                            }.all { it /*if all filters are true*/ }

                            // filter accounts matching the search and custom filters predicate
                            searchTermPredicate && filtersPredicate
                        }
                        .toHomeCryptoAssets()
                        .let { accounts ->
                            // <display list / isFullList>
                            accounts.take(state.sectionSize.size) to (accounts.size > state.sectionSize.size)
                        }
                },
                fiatAssets = DataResource.Data(emptyList()),
                filters = filters
            )
        }
    }

    private fun List<ModelAccount>.toHomeCryptoAssets(): List<CryptoAssetState> {
        val grouped = sortedWith(
            compareByDescending<ModelAccount> { it.singleAccount.currency.index }
                .thenBy {
                    it.singleAccount.currency.name
                }
        )
            .groupBy(
                keySelector = {
                    it.singleAccount.currency.networkTicker
                }
            )

        return grouped.values.map {
            CryptoAssetState(
                icon = it.first().singleAccount.currency.logo,
                name = it.first().singleAccount.currency.name,
                balance = it.map { acc -> acc.balance }.sumAvailableBalances(),
                fiatBalance = it.map { acc -> acc.fiatBalance }.sumAvailableBalances(),
                change = it.first().exchangeRateDayDelta.map { value ->
                    ValueChange.fromValue(value)
                }
            )
        }.sortedWith(
            object : Comparator<CryptoAssetState> {
                override fun compare(p0: CryptoAssetState, p1: CryptoAssetState): Int {
                    val p0Balance = (p0.fiatBalance as? DataResource.Data) ?: return 0
                    val p1Balance = (p1.fiatBalance as? DataResource.Data) ?: return 0
                    return p1Balance.data.compareTo(p0Balance.data)
                }
            }
        )
    }

    override suspend fun handleIntent(modelState: AssetsModelState, intent: AssetsIntent) {
        when (intent) {
            is AssetsIntent.LoadAccounts -> {
                updateState { it.copy(sectionSize = intent.sectionSize) }
                loadAccounts()
            }

            is AssetsIntent.LoadFilters -> {
                updateState {
                    it.copy(filters = filterService.toFilterStatus())
                }
            }

            is AssetsIntent.FilterSearch -> {
                updateState {
                    it.copy(filterTerm = intent.term)
                }
            }

            is AssetsIntent.UpdateFilters -> {
                filterService.fromFilterStatus(intent.filters)
                updateState {
                    it.copy(filters = intent.filters)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadAccounts() {
        accountsJob?.cancel()
        accountsJob = viewModelScope.launch {
            homeAccountsService.accounts()
                .onStart {
                    updateState { state ->
                        state.copy(
                            accounts = DataResource.Loading
                        )
                    }
                }
                .filterIsInstance<DataResource.Data<List<SingleAccount>>>()
                .distinctUntilChanged { old, new ->
                    val oldAssets = old.data.map { it.currency.networkTicker }
                    val newAssets = new.data.map { it.currency.networkTicker }
                    newAssets.isNotEmpty() && oldAssets.size == newAssets.size && oldAssets.containsAll(newAssets)
                }
                .onEach { data ->
                    updateState { state ->
                        state.copy(
                            accounts = DataResource.Data(
                                data.data.map { account ->
                                    ModelAccount(
                                        singleAccount = account,
                                        balance = DataResource.Loading,
                                        exchangeRateDayDelta = DataResource.Loading,
                                        fiatBalance = DataResource.Loading,
                                        usdRate = DataResource.Loading
                                    )
                                }
                            )
                        )
                    }
                }
                .filterIsInstance<DataResource.Data<List<SingleAccount>>>()
                .flatMapLatest { accounts ->
                    val balances = accounts.data.map { account ->
                        account.balance.distinctUntilChanged()
                            .map { it to account }
                    }.merge().onEach { (balance, account) ->
                        updateState { state ->
                            state.copy(
                                accounts = state.accounts.withBalancedAccount(
                                    account = account,
                                    balance = balance,
                                )
                            )
                        }
                    }

                    val usdRate = accounts.data.map { account ->
                        exchangeRates.exchangeRate(fromAsset = account.currency, toAsset = FiatCurrency.Dollars)
                            .map { it to account }
                    }.merge().onEach { (usdExchangeRate, account) ->
                        updateState { state ->
                            state.copy(
                                accounts = state.accounts.withUsdRate(
                                    account = account,
                                    usdRate = usdExchangeRate
                                )
                            )
                        }
                    }

                    val exchangeRates = accounts.data.map { account ->
                        exchangeRates.getPricesWith24hDelta(fromAsset = account.currency)
                            .map { it to account }
                    }.merge().onEach { (price, account) ->
                        updateState { state ->
                            state.copy(
                                accounts = state.accounts.withPricing(account, price)
                            )
                        }
                    }
                    merge(usdRate, balances, exchangeRates)
                }.collect()
        }
    }

    private fun DataResource<List<ModelAccount>>.totalBalance(): DataResource<Money> {
        return this.map {
            it.totalAccounts()
        }
    }

    private fun List<ModelAccount>.totalAccounts(): Money {
        return map { it.fiatBalance }.filterIsInstance<DataResource.Data<Money>>()
            .map { it.data }
            .fold(Money.zero(currencyPrefs.selectedFiatCurrency)) { acc, t ->
                acc.plus(t)
            }
    }
}

private fun List<DataResource<Money>>.sumAvailableBalances(): DataResource<Money> {
    var total: DataResource<Money>? = null
    forEach { money ->
        total = when (total) {
            is DataResource.Loading,
            is DataResource.Error,
            null -> money
            is DataResource.Data -> DataResource.Data(
                (total as DataResource.Data<Money>).data.plus(
                    (money as? DataResource.Data)?.data
                        ?: Money.zero((total as DataResource.Data<Money>).data.currency)
                )
            )
        }
    }
    return total!!
}

private fun DataResource<List<ModelAccount>>.withBalancedAccount(
    account: SingleAccount,
    balance: AccountBalance
): DataResource<List<ModelAccount>> {
    return this.map { accounts ->
        val oldAccount = accounts.first { it.singleAccount == account }
        accounts.replace(
            old = oldAccount,
            new = oldAccount.copy(
                balance = DataResource.Data(balance.total),
                fiatBalance = DataResource.Data(balance.totalFiat)
            )
        )
    }
}

private fun DataResource<List<ModelAccount>>.withUsdRate(
    account: SingleAccount,
    usdRate: DataResource<ExchangeRate>
): DataResource<List<ModelAccount>> {
    return this.map { accounts ->
        val oldAccount = accounts.first { it.singleAccount == account }
        accounts.replace(
            old = oldAccount,
            new = oldAccount.copy(
                usdRate = oldAccount.usdRate.updateDataWith(usdRate)
            )
        )
    }
}

private fun DataResource<List<ModelAccount>>.withPricing(
    account: SingleAccount,
    price: DataResource<Prices24HrWithDelta>
): DataResource<List<ModelAccount>> {
    return this.map { accounts ->
        val oldAccount = accounts.first { it.singleAccount == account }
        accounts.replace(
            old = oldAccount,
            new = oldAccount.copy(
                exchangeRateDayDelta = oldAccount.exchangeRateDayDelta.updateDataWith(
                    price.map {
                        it.delta24h
                    }
                )
            )
        )
    }
}

private fun MultiAppAssetsFilterService.toFilterStatus(): List<AssetFilterStatus> {
    val allFilters = listOf<AssetFilter>(AssetFilter.ShowSmallBalances)

    return allFilters.map { filter ->
        AssetFilterStatus(
            filter = filter,
            isEnabled = when (filter) {
                AssetFilter.ShowSmallBalances -> shouldShowSmallBalances
            }
        )
    }
}

private fun MultiAppAssetsFilterService.fromFilterStatus(filters: List<AssetFilterStatus>) {
    filters.forEach { assetFilter ->
        when (assetFilter.filter) {
            AssetFilter.ShowSmallBalances -> shouldShowSmallBalances = assetFilter.isEnabled
        }
    }
}
