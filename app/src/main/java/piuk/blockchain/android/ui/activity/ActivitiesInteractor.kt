package piuk.blockchain.android.ui.activity

import com.blockchain.analytics.Analytics
import com.blockchain.coincore.ActivitySummaryList
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.Coincore
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.preferences.SimpleBuyPrefs
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import piuk.blockchain.android.domain.repositories.AssetActivityRepository
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import timber.log.Timber

class ActivitiesInteractor(
    private val coincore: Coincore,
    private val activityRepository: AssetActivityRepository,
    private val custodialWalletManager: CustodialWalletManager,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val analytics: Analytics
) {
    fun getActivityForAccount(
        account: BlockchainAccount,
        isRefreshRequested: Boolean
    ): Observable<ActivitySummaryList> =
        activityRepository.fetch(account, isRefreshRequested)

    fun getDefaultAccount(): Single<BlockchainAccount> =
        coincore.allWallets().map { it }

    fun cancelSimpleBuyOrder(orderId: String): Disposable? {
        return custodialWalletManager.deleteBuyOrder(orderId)
            .subscribeBy(
                onComplete = { simpleBuyPrefs.clearBuyState() },
                onError = { error ->
                    analytics.logEvent(SimpleBuyAnalytics.BANK_DETAILS_CANCEL_ERROR)
                    Timber.e(error)
                }
            )
    }
}
