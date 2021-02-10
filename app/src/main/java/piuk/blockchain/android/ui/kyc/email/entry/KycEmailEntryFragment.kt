package piuk.blockchain.android.ui.kyc.email.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.blockchain.koin.scopedInject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentKycAddEmailBinding
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.androidcore.data.settings.Email

class KycEmailEntryFragment : MviFragment<EmailVeriffModel, EmailVeriffIntent, EmailVeriffState>(),
    SlidingModalBottomDialog.Host {

    private val emailEntryHost: EmailEntryHost by ParentActivityDelegate(
        this
    )

    private var _binding: FragmentKycAddEmailBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKycAddEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emailEntryHost.onEmailEntryFragmentShown()
        model.process(EmailVeriffIntent.StartEmailVerification)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override val model: EmailVeriffModel by scopedInject()

    override fun render(newState: EmailVeriffState) {
        if (newState.email.verified) {
            drawVerifiedEmailUi()
        } else {
            drawUnVerifiedEmailUi(newState.email)
        }
    }

    private fun drawVerifiedEmailUi() {
        binding.emailInstructions.text = getString(R.string.success_email_veriff)
        binding.emailStatusText.text = getString(R.string.email_verified)
        binding.txStateIndicator.setImageResource(R.drawable.ic_check_circle)
        binding.txStateIndicator.visible()
        binding.editEmailAddress.gone()
    }

    private fun drawUnVerifiedEmailUi(email: Email) {
        binding.emailInstructions.text = getString(R.string.sent_email_verification, email.address)
        binding.emailStatusText.text = getString(R.string.email_verify)
        binding.txStateIndicator.gone()
        binding.editEmailAddress.visible()

        binding.editEmailAddress.setOnClickListener {
            model.process(EmailVeriffIntent.CancelEmailVerification)
            EditEmailAddressBottomSheet.newInstance(email.address).show(childFragmentManager, "BOTTOM_SHEET")
        }
    }

    override fun onSheetClosed() {
        model.process(EmailVeriffIntent.StartEmailVerification)
    }
}

interface EmailEntryHost {
    fun onEmailEntryFragmentShown()
}