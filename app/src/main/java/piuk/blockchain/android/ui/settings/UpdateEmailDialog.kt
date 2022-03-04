package piuk.blockchain.android.ui.settings

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.viewextensions.getAlertDialogPaddedView
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar
import piuk.blockchain.android.util.FormatChecker

internal fun showUpdateEmailDialog(
    activity: Context,
    settingsPresenter: SettingsPresenter,
    currentEmail: String,
    isEmailVerified: Boolean,
    formatChecker: FormatChecker
) {
    val editText = AppCompatEditText(activity)
        .apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(currentEmail)
            setSelection(text?.length ?: 0)
        }

    val alertDialog = AlertDialog.Builder(activity, R.style.AlertDialogStyle)
        .setTitle(R.string.email)
        .setMessage(R.string.verify_email2)
        .setView(activity.getAlertDialogPaddedView(editText))
        .setCancelable(false)
        .setPositiveButton(R.string.update) { _, _ ->
            val newEmail = editText.text.toString()
            if (!formatChecker.isValidEmailAddress(newEmail)) {
                BlockchainSnackbar.make(
                    editText, activity.getString(R.string.invalid_email), type = SnackbarType.Error
                ).show()
            } else {
                settingsPresenter.updateEmail(newEmail)
            }
        }
        .also {
            if (!isEmailVerified) {
                it.setNeutralButton(R.string.resend) { _, _ ->
                    // Resend verification code
                    settingsPresenter.updateEmail(currentEmail)
                }
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()

    alertDialog.show()

    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

    editText.addTextChangedListener(object : TextWatcher {

        override fun afterTextChanged(s: Editable) {
            val email = s.toString()
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                email != currentEmail && formatChecker.isValidEmailAddress(email)
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
