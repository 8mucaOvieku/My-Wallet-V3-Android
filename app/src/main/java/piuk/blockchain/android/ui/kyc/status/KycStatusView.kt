package piuk.blockchain.android.ui.kyc.status

import androidx.annotation.StringRes
import com.blockchain.nabu.api.kyc.domain.model.KycTierState
import piuk.blockchain.android.ui.base.View

interface KycStatusView : View {

    fun finishPage()

    fun renderUi(kycState: KycTierState)

    fun showProgressDialog()

    fun dismissProgressDialog()

    fun startExchange()

    fun showSnackbar(@StringRes message: Int)

    fun showNotificationsEnabledDialog()
}
