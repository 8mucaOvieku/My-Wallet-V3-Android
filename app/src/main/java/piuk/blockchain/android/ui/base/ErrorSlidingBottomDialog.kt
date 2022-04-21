package piuk.blockchain.android.ui.base

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import com.blockchain.commonarch.presentation.base.SlidingModalBottomDialog
import java.lang.IllegalStateException
import kotlinx.parcelize.Parcelize
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ErrorSlidingBottomDialogBinding
import piuk.blockchain.android.simplebuy.ClientErrorAnalytics
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class ErrorSlidingBottomDialog : SlidingModalBottomDialog<ErrorSlidingBottomDialogBinding>() {

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): ErrorSlidingBottomDialogBinding =
        ErrorSlidingBottomDialogBinding.inflate(inflater, container, false)

    private val errorDialogData: ErrorDialogData by unsafeLazy {
        arguments?.getParcelable(ERROR_DIALOG_DATA_KEY) as? ErrorDialogData
            ?: throw IllegalStateException("No Dialog date provided")
    }

    override fun initControls(binding: ErrorSlidingBottomDialogBinding) {
        binding.title.text = errorDialogData.title
        binding.description.text = errorDialogData.description
        binding.ctaButton.text = errorDialogData.buttonText

        binding.ctaButton.setOnClickListener {
            dismiss()
        }
        logClientError(title = errorDialogData.description, error = errorDialogData.title)
    }

    private fun logClientError(title: String, error: String) {
        analytics.logEvent(
            ClientErrorAnalytics.ClientLogError(
                nabuApiException = null,
                error = error,
                source = ClientErrorAnalytics.Companion.Source.NABU,
                title = title,
                action = ClientErrorAnalytics.ACTION_BUY,
            )
        )
    }

    companion object {
        private const val ERROR_DIALOG_DATA_KEY = "ERROR_DIALOG_DATA_KEY"
        fun newInstance(errorDialogData: ErrorDialogData): ErrorSlidingBottomDialog =
            ErrorSlidingBottomDialog().apply {
                arguments = Bundle().apply { putParcelable(ERROR_DIALOG_DATA_KEY, errorDialogData) }
            }

        @Deprecated("Don't use this method. Always, have specific errors ")
        fun newInstance(context: Context): ErrorSlidingBottomDialog =
            ErrorSlidingBottomDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(
                        ERROR_DIALOG_DATA_KEY,
                        ErrorDialogData(
                            context.getString(R.string.ops),
                            context.getString(R.string.something_went_wrong_try_again),
                            context.getString(R.string.common_ok)
                        )
                    )
                }
            }
    }
}

@Parcelize
data class ErrorDialogData(val title: String, val description: String, val buttonText: String) : Parcelable
