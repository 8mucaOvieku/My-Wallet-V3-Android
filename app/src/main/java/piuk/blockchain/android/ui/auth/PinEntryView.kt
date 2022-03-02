package piuk.blockchain.android.ui.auth

import androidx.annotation.StringRes
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.viewextensions.Visibility
import piuk.blockchain.android.ui.base.View

interface PinEntryView : View {

    fun showProgressDialog(@StringRes messageId: Int)

    fun fillPinBoxAtIndex(index: Int)

    fun clearPinBoxAtIndex(index: Int)

    fun fillPinBoxes()

    fun showSnackbar(@StringRes message: Int, type: SnackbarType, doOnDismiss: () -> Unit = {})

    fun showParameteredSnackbar(
        @StringRes message: Int,
        type: SnackbarType,
        parameter: Int,
        doOnDismiss: () -> Unit = {}
    )

    fun dismissProgressDialog()

    fun showMaxAttemptsDialog()

    fun showValidationDialog()

    fun showCommonPinWarning(callback: DialogButtonCallback)

    fun showWalletVersionNotSupportedDialog(walletVersion: String?)

    fun walletUpgradeRequired(passwordTriesRemaining: Int, isFromPinCreation: Boolean)

    fun onWalletUpgradeFailed()

    fun restartPageAndClearTop()

    fun setTitleString(@StringRes title: Int)

    fun setTitleVisibility(@Visibility visibility: Int)

    fun clearPinBoxes()

    fun goToPasswordRequiredActivity()

    fun finishWithPayloadDecrypted()

    fun finishWithResultOk(pin: String)

    fun showFingerprintDialog()

    fun showKeyboard()

    fun showAccountLockedDialog()

    fun showMobileNotice(mobileNoticeDialog: MobileNoticeDialog)

    fun appNeedsUpgrade(isForced: Boolean)

    fun restartAppWithVerifiedPin()

    fun closePinChangeScreen()

    fun setupCommitHashView()

    fun askToUseBiometrics()

    fun showApiOutageMessage()

    val isForValidatingPinForResult: Boolean

    val isSettingsOrigin: Boolean

    val isForValidatingAndLoadingPayloadResult: Boolean
}
