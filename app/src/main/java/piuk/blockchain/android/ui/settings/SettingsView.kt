package piuk.blockchain.android.ui.settings

import androidx.annotation.StringRes
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.core.payments.model.LinkBankTransfer
import com.blockchain.nabu.datamanagers.PaymentMethod
import piuk.blockchain.android.scan.QrScanError
import piuk.blockchain.android.ui.base.View

interface SettingsView : View {

    fun setUpUi()

    fun showFingerprintDialog(pincode: String)

    fun showDisableFingerprintDialog()

    fun updateFingerprintPreferenceStatus()

    fun showNoFingerprintsAddedDialog()

    fun showProgress()

    fun hideProgress()

    fun showSnackbar(@StringRes stringId: Int, type: SnackbarType = SnackbarType.Error)

    fun setGuidSummary(summary: String)

    fun setEmailSummary(email: String, isVerified: Boolean)
    fun setEmailUnknown()

    fun setSmsSummary(smsNumber: String, isVerified: Boolean)
    fun setSmsUnknown()

    fun setFiatSummary(summary: String)

    fun showEmailDialog(currentEmail: String, emailVerified: Boolean)

    fun showDialogTwoFA(authType: Int, smsVerified: Boolean)

    fun setEmailNotificationsVisibility(visible: Boolean)

    fun setEmailNotificationPref(enabled: Boolean)

    fun setPushNotificationPref(enabled: Boolean)

    fun setFingerprintVisibility(visible: Boolean)

    fun setTwoFaPreference(enabled: Boolean)

    fun setTorBlocked(blocked: Boolean)

    fun setPitLinkingState(isLinked: Boolean)

    fun updateCards(cards: List<PaymentMethod.Card>)

    fun updateLinkNewBank(linkablePaymentMethods: LinkablePaymentMethods)

    fun updateLinkedBanks(banks: List<BankItem>)

    fun cardsEnabled(enabled: Boolean)

    fun addCardEnabled(enabled: Boolean)

    fun banksEnabled(enabled: Boolean)

    fun setScreenshotsEnabled(enabled: Boolean)

    fun showDialogEmailVerification()

    fun showDialogVerifySms()

    fun showDialogMobile(authType: Int, isSmsVerified: Boolean, smsNumber: String)

    fun showDialogSmsVerified()

    fun goToPinEntryPage()

    fun launchThePitLandingActivity()

    fun launchThePit()

    fun setLauncherShortcutVisibility(visible: Boolean)

    fun showWarningDialog(@StringRes message: Int)

    fun linkBankWithPartner(linkBankTransfer: LinkBankTransfer)

    fun showRateUsPreference()

    fun showScanTargetError(error: QrScanError)
}
