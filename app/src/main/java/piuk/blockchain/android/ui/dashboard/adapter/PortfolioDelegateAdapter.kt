package piuk.blockchain.android.ui.dashboard.adapter

import com.blockchain.analytics.Analytics
import com.blockchain.coincore.FiatAccount
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.AssetInfo
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.dashboard.announcements.ApiAnnouncementDelegate
import piuk.blockchain.android.ui.dashboard.announcements.MiniAnnouncementDelegate
import piuk.blockchain.android.ui.dashboard.announcements.StdAnnouncementDelegate
import piuk.blockchain.android.ui.dashboard.model.Locks
import piuk.blockchain.android.ui.resources.AssetResources

class PortfolioDelegateAdapter(
    prefs: CurrencyPrefs,
    onCardClicked: (AssetInfo) -> Unit,
    analytics: Analytics,
    onFundsItemClicked: (FiatAccount) -> Unit,
    onHoldAmountClicked: (Locks) -> Unit,
    assetResources: AssetResources
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {

    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(StdAnnouncementDelegate(analytics))
            addAdapterDelegate(ApiAnnouncementDelegate(analytics))
            addAdapterDelegate(FundsLockedDelegate(onHoldAmountClicked))
            addAdapterDelegate(MiniAnnouncementDelegate(analytics))
            addAdapterDelegate(BalanceCardDelegate(prefs.selectedFiatCurrency, assetResources))
            addAdapterDelegate(
                FundsCardDelegate(
                    prefs.selectedFiatCurrency,
                    onFundsItemClicked
                )
            )
            addAdapterDelegate(AssetCardDelegate(prefs, assetResources, onCardClicked))
            addAdapterDelegate(EmptyCardDelegate())
        }
    }
}
