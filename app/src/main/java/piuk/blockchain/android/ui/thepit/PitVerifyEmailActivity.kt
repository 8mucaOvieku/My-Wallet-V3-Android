package piuk.blockchain.android.ui.thepit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.databinding.ToolbarGeneralBinding
import com.blockchain.koin.scopedInject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ActivityPitVerifyEmailLayoutBinding
import piuk.blockchain.android.ui.base.BaseMvpActivity
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar

class PitVerifyEmailActivity : BaseMvpActivity<PitVerifyEmailView, PitVerifyEmailPresenter>(), PitVerifyEmailView {

    private val pitVerifyEmailPresenter: PitVerifyEmailPresenter by scopedInject()

    private val binding: ActivityPitVerifyEmailLayoutBinding by lazy {
        ActivityPitVerifyEmailLayoutBinding.inflate(layoutInflater)
    }

    override val toolbarBinding: ToolbarGeneralBinding
        get() = binding.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateToolbar(
            toolbarTitle = getString(R.string.the_exchange_verify_email_title),
            backAction = { onBackPressed() }
        )
        val email = intent.getStringExtra(ARGUMENT_EMAIL) ?: ""
        with(binding) {
            emailAddress.text = email

            sendAgain.setOnClickListener {
                presenter?.resendMail(email)
            }

            openApp.setOnClickListener {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.security_centre_email_check)))
            }
        }

        presenter?.onViewReady()

        // We want to resend the email verification email so that the resent email verification contains the
        // context that the user is trying to link from the Pit.
        presenter?.resendMail(email)
    }

    override fun createPresenter() = pitVerifyEmailPresenter

    override fun getView(): PitVerifyEmailView = this

    override fun mailResendFailed() {
        BlockchainSnackbar.make(
            binding.root, getString(R.string.mail_resent_failed), type = SnackbarType.Error
        ).show()
    }

    override fun mailResentSuccessfully() {
        BlockchainSnackbar.make(
            binding.root, getString(R.string.mail_resent_succeed), type = SnackbarType.Success
        ).show()
    }

    override fun emailVerified() {
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val ARGUMENT_EMAIL = "email"

        fun start(ctx: AppCompatActivity, email: String, requestCode: Int) {
            val intent = Intent(ctx, PitVerifyEmailActivity::class.java).apply {
                putExtra(ARGUMENT_EMAIL, email)
            }
            ctx.startActivityForResult(intent, requestCode)
        }
    }
}
