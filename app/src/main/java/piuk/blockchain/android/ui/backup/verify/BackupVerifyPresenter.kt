package piuk.blockchain.android.ui.backup.verify

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.preferences.WalletStatus
import io.reactivex.rxjava3.kotlin.plusAssign
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.backup.wordlist.BackupWalletWordListFragment.Companion.ARGUMENT_SECOND_PASSWORD
import piuk.blockchain.android.ui.base.BasePresenter
import piuk.blockchain.android.ui.base.View
import piuk.blockchain.android.util.BackupWalletUtil
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

interface BackupVerifyView : View {
    fun getPageBundle(): Bundle?
    fun showProgressDialog()
    fun hideProgressDialog()
    fun showSnackbar(@StringRes message: Int, type: SnackbarType)
    fun showCompletedFragment()
    fun showStartingFragment()
    fun showWordHints(hints: List<Int>)
}

class BackupVerifyPresenter(
    private val payloadDataManager: PayloadDataManager,
    private val walletStatus: WalletStatus,
    private val backupWalletUtil: BackupWalletUtil
) : BasePresenter<BackupVerifyView>() {

    private val sequence by unsafeLazy { getBackupConfirmSequence() }

    override fun onViewReady() {
        view.showWordHints(listOf(sequence[0].first, sequence[1].first, sequence[2].first))
    }

    internal fun onVerifyClicked(firstWord: String, secondWord: String, thirdWord: String) {
        if (firstWord.trim { it <= ' ' }.equals(sequence[0].second, ignoreCase = true) &&
            secondWord.trim { it <= ' ' }.equals(sequence[1].second, ignoreCase = true) &&
            thirdWord.trim { it <= ' ' }.equals(sequence[2].second, ignoreCase = true)
        ) {

            updateBackupStatus()
        } else {
            view.showSnackbar(R.string.backup_word_mismatch, SnackbarType.Error)
        }
    }

    @SuppressLint("CheckResult")
    @VisibleForTesting
    internal fun updateBackupStatus() {
        payloadDataManager.wallet!!.walletBody?.mnemonicVerified = true

        compositeDisposable += payloadDataManager.syncPayloadWithServer()
            .doOnSubscribe { view.showProgressDialog() }
            .doAfterTerminate { view.hideProgressDialog() }
            .subscribe(
                {
                    walletStatus.lastBackupTime = System.currentTimeMillis() / 1000
                    view.showSnackbar(R.string.backup_confirmed, SnackbarType.Success)
                    view.showCompletedFragment()
                },
                { throwable ->
                    Timber.e(throwable)
                    view.showSnackbar(R.string.api_fail, SnackbarType.Error)
                    view.showStartingFragment()
                }
            )
    }

    private fun getBackupConfirmSequence(): List<Pair<Int, String>> {
        val bundle = view.getPageBundle()
        val secondPassword = bundle?.getString(ARGUMENT_SECOND_PASSWORD)
        return backupWalletUtil.getConfirmSequence(secondPassword)
    }
}
