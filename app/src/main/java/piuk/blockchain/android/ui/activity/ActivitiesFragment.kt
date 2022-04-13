package piuk.blockchain.android.ui.activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.analytics.events.ActivityAnalytics
import com.blockchain.analytics.events.LaunchOrigin
import com.blockchain.annotations.CommonCode
import com.blockchain.coincore.ActivitySummaryItem
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAccount
import com.blockchain.commonarch.presentation.base.BlockchainActivity
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.core.price.historic.HistoricRateFetcher
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import com.google.android.material.snackbar.Snackbar
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Currency
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.asAssetInfoOrThrow
import info.blockchain.balance.asFiatCurrencyOrThrow
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentActivitiesBinding
import piuk.blockchain.android.ui.activity.adapter.ActivitiesDelegateAdapter
import piuk.blockchain.android.ui.activity.detail.CryptoActivityDetailsBottomSheet
import piuk.blockchain.android.ui.activity.detail.FiatActivityDetailsBottomSheet
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar
import piuk.blockchain.android.ui.customviews.account.AccountSelectSheet
import piuk.blockchain.android.ui.home.HomeScreenMviFragment
import piuk.blockchain.android.ui.recurringbuy.RecurringBuyAnalytics
import piuk.blockchain.android.ui.resources.AccountIcon
import piuk.blockchain.android.ui.resources.AssetResources
import piuk.blockchain.android.util.getAccount
import piuk.blockchain.android.util.putAccount
import piuk.blockchain.android.util.setAssetIconColoursNoTint
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

class ActivitiesFragment :
    HomeScreenMviFragment<ActivitiesModel, ActivitiesIntent, ActivitiesState, FragmentActivitiesBinding>(),
    AccountSelectSheet.SelectionHost,
    CryptoActivityDetailsBottomSheet.Host {

    override val model: ActivitiesModel by scopedInject()

    private val activityAdapter: ActivitiesDelegateAdapter by lazy {
        ActivitiesDelegateAdapter(
            prefs = get(),
            historicRateFetcher = historicRateFetcher,
            onItemClicked = { currency, tx, type ->
                onItemClicked(currency, tx, type)
            }
        )
    }

    private val displayList = mutableListOf<ActivitySummaryItem>()

    private val disposables = CompositeDisposable()
    private val rxBus: RxBus by inject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val assetResources: AssetResources by inject()
    private val historicRateFetcher: HistoricRateFetcher by scopedInject()

    private val actionEvent by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    private var state: ActivitiesState? = null
    private var selectedFiatCurrency: FiatCurrency? = null

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentActivitiesBinding =
        FragmentActivitiesBinding.inflate(inflater, container, false)

    @UiThread
    override fun render(newState: ActivitiesState) {
        if (newState.isError) {
            BlockchainSnackbar.make(
                binding.root,
                getString(R.string.activity_loading_error),
                duration = Snackbar.LENGTH_SHORT,
                type = SnackbarType.Error
            ).show()
        }

        switchView(newState)

        renderLoader(newState)
        renderAccountDetails(newState)
        renderTransactionList(newState)

        if (this.state?.bottomSheet != newState.bottomSheet) {
            when (newState.bottomSheet) {
                ActivitiesSheet.ACCOUNT_SELECTOR -> {
                    analytics.logEvent(ActivityAnalytics.WALLET_PICKER_SHOWN)
                    showBottomSheet(AccountSelectSheet.newInstance(this))
                }
                ActivitiesSheet.CRYPTO_ACTIVITY_DETAILS -> {
                    newState.selectedCurrency?.asAssetInfoOrThrow()?.let {
                        showBottomSheet(
                            CryptoActivityDetailsBottomSheet.newInstance(
                                it, newState.selectedTxId,
                                newState.activityType
                            )
                        )
                    }
                }
                ActivitiesSheet.FIAT_ACTIVITY_DETAILS -> {
                    newState.selectedCurrency?.asFiatCurrencyOrThrow()?.let {
                        showBottomSheet(
                            FiatActivityDetailsBottomSheet.newInstance(it, newState.selectedTxId)
                        )
                    }
                }
            }
        }
        this.state = newState
    }

    private fun switchView(newState: ActivitiesState) {
        with(binding) {
            when {
                newState.isLoading && newState.activityList.isEmpty() -> {
                    headerLayout.gone()
                    contentList.gone()
                    emptyView.gone()
                }
                newState.activityList.isEmpty() -> {
                    headerLayout.visible()
                    contentList.gone()
                    emptyView.visible()
                }
                else -> {
                    headerLayout.visible()
                    contentList.visible()
                    emptyView.gone()
                }
            }
        }
    }

    private fun sendAnalyticsOnItemClickEvent(type: ActivityType, assetInfo: AssetInfo) {
        if (type == ActivityType.RECURRING_BUY) {
            analytics.logEvent(
                RecurringBuyAnalytics.RecurringBuyDetailsClicked(
                    LaunchOrigin.TRANSACTION_LIST,
                    assetInfo.networkTicker
                )
            )
        }
    }

    private fun renderAccountDetails(newState: ActivitiesState) {
        if (newState.account == state?.account && selectedFiatCurrency == currencyPrefs.selectedFiatCurrency) {
            return
        }

        if (newState.account == null) {
            // Should not happen! TODO: Crash
            return
        }

        with(binding) {
            disposables.clear()

            val account = newState.account

            val accIcon = AccountIcon(account, assetResources)
            accIcon.loadAssetIcon(accountIcon)

            accIcon.indicator?.let {
                check(account is CryptoAccount) {
                    "Indicators are supported only for CryptoAccounts"
                }
                val currency = account.currency
                accountIndicator.apply {
                    visible()
                    setImageResource(it)
                    setAssetIconColoursNoTint(currency)
                }
            } ?: accountIndicator.gone()

            accountName.text = account.label
            fiatBalance.text = ""
            selectedFiatCurrency = currencyPrefs.selectedFiatCurrency

            disposables += account.balance.firstOrError().map {
                it.totalFiat
            }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = {
                        fiatBalance.text =
                            getString(
                                R.string.common_spaced_strings, it.toStringWithSymbol(),
                                it.currencyCode
                            )
                    },
                    onError = {
                        Timber.e("Unable to get balance for ${account.label}")
                    }
                )
            accountSelectBtn.visible()
        }
    }

    private fun renderTransactionList(newState: ActivitiesState) {
        if (state?.activityList == newState.activityList) {
            return
        }

        with(newState.activityList) {
            displayList.clear()
            if (isEmpty()) {
                Timber.d("Render new tx list - empty")
            } else {
                displayList.addAll(this)
            }
            activityAdapter.notifyDataSetChanged()
        }
    }

    private fun renderLoader(newState: ActivitiesState) {
        val blockchainActivity = (activity as? BlockchainActivity) ?: return

        if (newState.isLoading) {
            binding.swipe.isRefreshing = newState.isRefreshRequested
            if (!newState.isRefreshRequested) {
                blockchainActivity.showLoading()
            }
        } else {
            blockchainActivity.hideLoading()
            binding.swipe.isRefreshing = false
        }
    }

    override fun onBackPressed(): Boolean = false

    private val preselectedAccount: BlockchainAccount?
        get() = arguments?.getAccount(PARAM_ACCOUNT)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preselectedAccount?.let {
            onAccountSelected(it)
        } ?: onShowAllActivity()

        setupSwipeRefresh()
        setupRecycler()
        setupAccountSelect()
    }

    private fun setupRecycler() {
        binding.contentList.apply {
            layoutManager = SafeLayoutManager(requireContext())
            adapter = activityAdapter
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
        }
        activityAdapter.items = displayList
    }

    private fun setupAccountSelect() {
        binding.accountSelectBtn.setOnClickListener {
            model.process(ShowAccountSelectionIntent)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipe.setOnRefreshListener {
            state?.account?.let {
                model.process(AccountSelectedIntent(it, true))
            }
        }

        // Configure the refreshing colors
        binding.swipe.setColorSchemeResources(
            R.color.blue_800,
            R.color.blue_600,
            R.color.blue_400,
            R.color.blue_200
        )
    }

    override fun onPause() {
        disposables.clear()
        rxBus.unregister(ActionEvent::class.java, actionEvent)
        super.onPause()
    }

    private fun onItemClicked(
        currency: Currency,
        txHash: String,
        type: ActivityType
    ) {
        model.process(ShowActivityDetailsIntent(currency, txHash, type))
    }

    private fun onShowAllActivity() {
        model.process(SelectDefaultAccountIntent)
    }

    override fun onAccountSelected(account: BlockchainAccount) {
        model.process(AccountSelectedIntent(account, false))
    }

    override fun onAddCash(currency: String) {
        navigator().launchFiatDeposit(currency)
    }

    // SlidingModalBottomDialog.Host
    override fun onSheetClosed() {
        model.process(ClearBottomSheetIntent)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            state?.account?.let {
                model.process(AccountSelectedIntent(it, true))
            }
        }
    }

    companion object {
        private const val PARAM_ACCOUNT = "PARAM_ACCOUNT"

        fun newInstance(account: BlockchainAccount? = null): ActivitiesFragment {
            return ActivitiesFragment().apply {
                arguments = Bundle().apply {
                    account?.let { putAccount(PARAM_ACCOUNT, it) }
                }
            }
        }
    }
}

/**
 * supportsPredictiveItemAnimations = false to avoid crashes when computing changes.
 */
@CommonCode(commonWith = "DashboardFragment - move to ui utils package")
private class SafeLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun supportsPredictiveItemAnimations() = false
}
