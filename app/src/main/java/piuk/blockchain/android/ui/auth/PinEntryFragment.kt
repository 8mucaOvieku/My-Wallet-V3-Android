package piuk.blockchain.android.ui.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.widget.AppCompatEditText
import com.blockchain.biometrics.BiometricAuthError
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthLockout
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthLockoutPermanent
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthOther
import com.blockchain.biometrics.BiometricAuthError.BiometricKeysInvalidated
import com.blockchain.biometrics.BiometricsCallback
import com.blockchain.biometrics.BiometricsType
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.legacy.MaterialProgressDialog
import com.blockchain.componentlib.viewextensions.Visibility
import com.blockchain.componentlib.viewextensions.getAlertDialogPaddedView
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.koin.scopedInject
import com.blockchain.ui.password.SecondPasswordHandler
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.android.ext.android.inject
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.data.biometrics.BiometricPromptUtil
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.data.biometrics.WalletBiometricData
import piuk.blockchain.android.data.connectivity.ConnectivityStatus
import piuk.blockchain.android.databinding.FragmentPinEntryBinding
import piuk.blockchain.android.ui.auth.PinEntryActivity.Companion.KEY_ORIGIN_SETTINGS
import piuk.blockchain.android.ui.base.BaseFragment
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar
import piuk.blockchain.android.ui.customviews.PinEntryKeypad
import piuk.blockchain.android.ui.home.MobileNoticeDialogFragment
import piuk.blockchain.android.ui.launcher.loader.LoaderActivity
import piuk.blockchain.android.ui.start.PasswordRequiredActivity
import piuk.blockchain.android.urllinks.APP_STORE_URI
import piuk.blockchain.android.urllinks.APP_STORE_URL
import piuk.blockchain.android.urllinks.WALLET_STATUS_URL
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.StringUtils.Companion.getStringWithMappedAnnotations
import piuk.blockchain.android.util.copyHashOnLongClick
import piuk.blockchain.android.util.scopedInjectActivity

class PinEntryFragment :
    BaseFragment<PinEntryView, PinEntryPresenter>(),
    PinEntryView,
    BiometricsEnrollmentBottomSheet.Host {

    private var _binding: FragmentPinEntryBinding? = null
    private val binding: FragmentPinEntryBinding
        get() = _binding!!

    private val pinEntryPresenter: PinEntryPresenter by scopedInject()
    private val environmentConfig: EnvironmentConfig by inject()
    private val biometricsController: BiometricsController by scopedInject()
    private val appUtil: AppUtil by inject()

    private val secondPasswordHandler: SecondPasswordHandler by scopedInjectActivity()

    private val pinBoxList = mutableListOf<ImageView>()

    private var materialProgressDialog: MaterialProgressDialog? = null
    private val clearPinNumberRunnable = ClearPinNumberRunnable()
    private var isPaused = false

    val isValidatingPinForResult: Boolean
        get() = presenter?.isForValidatingPinForResult ?: false

    private val compositeDisposable = CompositeDisposable()

    private val isAfterWalletCreation: Boolean by lazy {
        arguments?.getBoolean(KEY_IS_AFTER_WALLET_CREATION, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set title state
        if (presenter.isCreatingNewPin) {
            binding.titleBox.setText(R.string.create_pin)
        } else {
            binding.titleBox.setText(R.string.pin_entry)
            presenter.fetchInfoMessage()
        }

        binding.let {
            pinBoxList.add(it.pinBox0)
            pinBoxList.add(it.pinBox1)
            pinBoxList.add(it.pinBox2)
            pinBoxList.add(it.pinBox3)
        }

        showConnectionDialogIfNeeded()

        presenter.onViewReady()
        presenter.checkForceUpgradeStatus(BuildConfig.VERSION_NAME)

        binding.keyboard.setPadClickedListener(
            object : PinEntryKeypad.OnPinEntryPadClickedListener {

                override fun onNumberClicked(number: String) {
                    presenter.onPadClicked(number)
                }

                override fun onDeleteClicked() {
                    presenter.onDeleteClicked()
                }
            })

        if (environmentConfig.isRunningInDebugMode()) {
            BlockchainSnackbar.make(
                binding.root,
                "Current environment: " + environmentConfig.environment.name,
                duration = Snackbar.LENGTH_SHORT,
            ).show()
        }

        binding.textViewVersionCode.text = getVersionText()
        binding.pinEntryLogout.setOnClickListener {
            presenter.resetApp()
        }
    }

    private fun getVersionText() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    override fun showFingerprintDialog() {
        binding.fingerprintLogo.visible()
        binding.fingerprintLogo.setOnClickListener { presenter.checkFingerprintStatus() }

        if (presenter.canShowFingerprintDialog()) {
            biometricsController.authenticate(
                this, BiometricsType.TYPE_LOGIN,
                object : BiometricsCallback<WalletBiometricData> {
                    override fun onAuthSuccess(unencryptedBiometricData: WalletBiometricData) {
                        presenter.loginWithDecryptedPin(unencryptedBiometricData.accessPin)
                    }

                    override fun onAuthFailed(error: BiometricAuthError) {
                        showKeyboard()
                        when (error) {
                            is BiometricAuthLockout -> BiometricPromptUtil.showAuthLockoutDialog(requireContext())
                            is BiometricAuthLockoutPermanent -> {
                                hideBiometricsUi()
                                BiometricPromptUtil.showPermanentAuthLockoutDialog(requireContext())
                            }
                            is BiometricKeysInvalidated -> {
                                hideBiometricsUi()
                                BiometricPromptUtil.showInfoInvalidatedKeysDialog(requireContext())
                            }
                            is BiometricAuthOther -> {
                                hideBiometricsUi()
                                BiometricPromptUtil.showBiometricsGenericError(requireContext(), error.error)
                            }
                            else -> {
                                // do nothing - this is handled by the Biometric Prompt framework
                            }
                        }
                    }

                    override fun onAuthCancelled() {
                        showKeyboard()
                    }
                }
            )

            hideKeyboard()
        }
    }

    private fun hideBiometricsUi() {
        showKeyboard()
        binding.fingerprintLogo.gone()
    }

    override fun askToUseBiometrics() {
        BiometricsEnrollmentBottomSheet.newInstance().show(childFragmentManager, "BOTTOM_SHEET")
    }

    override fun showApiOutageMessage() {
        binding.layoutWarning.root.visible()
        val learnMoreMap = mapOf<String, Uri>("learn_more" to Uri.parse(WALLET_STATUS_URL))
        binding.layoutWarning.warningMessage.let {
            it.movementMethod = LinkMovementMethod.getInstance()
            it.text = getStringWithMappedAnnotations(
                requireActivity(), R.string.wallet_issue_message, learnMoreMap
            )
        }
    }

    override val isForValidatingPinForResult: Boolean
        get() = activity?.intent?.extras?.getBoolean(KEY_VALIDATING_PIN_FOR_RESULT, false)
            ?: false
    override val isForValidatingAndLoadingPayloadResult: Boolean
        get() = activity?.intent?.extras?.getBoolean(KEY_VALIDATING_PIN_FOR_RESULT_AND_PAYLOAD, false)
            ?: false

    override val isSettingsOrigin: Boolean
        get() = activity?.intent?.extras?.getBoolean(KEY_ORIGIN_SETTINGS, false) ?: false

    override fun enrollBiometrics() {
        biometricsController.authenticate(
            this, BiometricsType.TYPE_REGISTER,
            object : BiometricsCallback<WalletBiometricData> {
                override fun onAuthSuccess(data: WalletBiometricData) {
                    restartAppWithVerifiedPin()
                }

                override fun onAuthFailed(error: BiometricAuthError) {
                    when (error) {
                        is BiometricAuthLockout -> BiometricPromptUtil.showAuthLockoutDialog(requireContext())
                        is BiometricAuthLockoutPermanent -> {
                            hideBiometricsUi()
                            BiometricPromptUtil.showPermanentAuthLockoutDialog(requireContext())
                        }
                        is BiometricKeysInvalidated -> {
                            hideBiometricsUi()
                            BiometricPromptUtil.showInfoInvalidatedKeysDialog(requireContext())
                        }
                        is BiometricAuthOther -> {
                            hideBiometricsUi()
                            BiometricPromptUtil.showBiometricsGenericError(requireContext(), error.error)
                        }
                        else -> {
                            // do nothing - this is handled by the Biometric Prompt framework
                        }
                    }
                }

                override fun onAuthCancelled() {
                    // do nothing, the sheet is not dismissed when the user starts the flow
                }
            }
        )
    }

    override fun cancel() {
        presenter.finishSignupProcess()
    }

    override fun onSheetClosed() {
        presenter.finishSignupProcess()
    }

    override fun showKeyboard() {
        if (activity != null && binding.keyboard.visibility == View.INVISIBLE) {
            val bottomUp = AnimationUtils.loadAnimation(activity, R.anim.bottom_up)
            binding.keyboard.startAnimation(bottomUp)
            binding.keyboard.visibility = View.VISIBLE
        }
    }

    private fun hideKeyboard() {
        if (activity != null && binding.keyboard.visibility == View.VISIBLE) {
            val bottomUp = AnimationUtils.loadAnimation(activity, R.anim.top_down)
            binding.keyboard.startAnimation(bottomUp)
            binding.keyboard.visibility = View.INVISIBLE
        }
    }

    private fun showConnectionDialogIfNeeded() {
        if (context != null) {
            if (!ConnectivityStatus.hasConnectivity(context)) {
                AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                    .setMessage(getString(R.string.check_connectivity_exit))
                    .setCancelable(false)
                    .setPositiveButton(
                        R.string.dialog_continue
                    ) { _, _ -> restartPageAndClearTop() }
                    .create()
                    .show()
            }
        }
    }

    override fun showMaxAttemptsDialog() {
        if (context != null) {
            AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(R.string.password_or_wipe)
                .setCancelable(true)
                .setPositiveButton(R.string.use_password) { _, _ -> showValidationDialog() }
                .setNegativeButton(R.string.common_cancel) { di, _ -> di.dismiss() }
                .show()
        }
    }

    fun onBackPressed() {
        if (presenter?.isForValidatingPinForResult == true) {
            finishWithResultCanceled()
        } else if (presenter?.allowExit() == true) {
            presenter.clearLoginState()
        }
    }

    override fun showWalletVersionNotSupportedDialog(walletVersion: String?) {
        if (context != null && walletVersion != null) {
            AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setMessage(
                    String.format(
                        getString(R.string.unsupported_encryption_version),
                        walletVersion
                    )
                )
                .setCancelable(false)
                .setPositiveButton(
                    R.string.exit
                ) { _, _ -> presenter.clearLoginState() }
                .setNegativeButton(R.string.logout) { _, _ ->
                    presenter.clearLoginState()
                    restartApp()
                }
                .show()
        }
    }

    private fun restartApp() {
        appUtil.restartApp()
    }

    override fun clearPinBoxes() {
        HANDLER.postDelayed(clearPinNumberRunnable, 200)
    }

    override fun goToPasswordRequiredActivity() {
        val intent = Intent(context, PasswordRequiredActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun walletUpgradeRequired(passwordTriesRemaining: Int, isFromPinCreation: Boolean) {
        secondPasswordHandler.validate(
            this.requireContext(),
            object : SecondPasswordHandler.ResultListener {
                override fun onNoSecondPassword() {
                    presenter.doUpgradeWallet(null, isFromPinCreation)
                }

                override fun onSecondPasswordValidated(validatedSecondPassword: String) {
                    presenter.doUpgradeWallet(validatedSecondPassword, isFromPinCreation)
                }

                override fun onCancelled() {
                    handleIncorrectPassword(passwordTriesRemaining, isFromPinCreation)
                }
            }
        )
    }

    private fun handleIncorrectPassword(triesRemaining: Int, isFromPinCreation: Boolean) {
        if (triesRemaining > 0) {
            walletUpgradeRequired(triesRemaining - 1, isFromPinCreation)
        } else {
            // TODO: Handle can't remember
        }
    }

    override fun onWalletUpgradeFailed() {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
            .setTitle(R.string.upgrade_fail_heading)
            .setMessage(R.string.upgrade_fail_info)
            .setCancelable(false)
            .setPositiveButton(R.string.exit) { _, _ -> presenter.clearLoginState() }
            .setNegativeButton(R.string.logout) { _, _ -> presenter.clearLoginState(); restartApp() }
            .show()
    }

    override fun setTitleString(@StringRes title: Int) {
        HANDLER.postDelayed({ binding.titleBox.setText(title) }, 200)
    }

    override fun setTitleVisibility(@Visibility visibility: Int) {
        binding.titleBox.visibility = visibility
    }

    fun allowExit(): Boolean {
        return presenter?.allowExit() ?: true
    }

    override fun restartPageAndClearTop() {
        val intent = Intent(context, PinEntryActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun showCommonPinWarning(callback: DialogButtonCallback) {
        if (context != null) {
            AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                .setTitle(R.string.common_pin_dialog_title)
                .setMessage(R.string.common_pin_dialog_message)
                .setPositiveButton(
                    R.string.common_pin_dialog_try_again
                ) { _, _ -> callback.onPositiveClicked() }
                .setNegativeButton(
                    R.string.common_pin_dialog_continue
                ) { _, _ -> callback.onNegativeClicked() }
                .setCancelable(false)
                .create()
                .show()
        }
    }

    override fun showValidationDialog() {
        context?.let { ctx ->
            val password = AppCompatEditText(ctx)
            password.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            password.setHint(R.string.password)

            AlertDialog.Builder(ctx, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.password_entry))
                .setView(ctx.getAlertDialogPaddedView(password))
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    restartApp()
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val pw = password.text.toString()

                    if (pw.isNotEmpty()) {
                        presenter.validatePassword(pw)
                    } else {
                        presenter.incrementFailureCountAndRestart()
                    }
                }.show()
        }
    }

    override fun showAccountLockedDialog() {
        if (context != null) {
            AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                .setTitle(R.string.account_locked_title)
                .setMessage(R.string.account_locked_message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit) { _, _ -> activity?.finish() }
                .create()
                .show()
        }
    }

    private fun isNotFinishing(): Boolean {
        val a = activity
        return (a != null && !a.isFinishing)
    }

    override fun showSnackbar(@StringRes message: Int, type: SnackbarType, doOnDismiss: () -> Unit) {
        if (isNotFinishing()) {
            BlockchainSnackbar.make(
                binding.root,
                getString(message),
                duration = Snackbar.LENGTH_SHORT,
                type = type
            ).addCallback(object : BaseTransientBottomBar.BaseCallback<BlockchainSnackbar>() {
                override fun onShown(transientBottomBar: BlockchainSnackbar) {
                    super.onShown(transientBottomBar)
                }

                override fun onDismissed(transientBottomBar: BlockchainSnackbar, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    doOnDismiss()
                }
            }).show()
        }
    }

    override fun showParameteredSnackbar(
        @StringRes message: Int,
        type: SnackbarType,
        parameter: Int,
        doOnDismiss: () -> Unit
    ) {
        if (isNotFinishing()) {
            BlockchainSnackbar.make(
                binding.root,
                getString(message, parameter),
                duration = Snackbar.LENGTH_SHORT,
                type = type
            ).addCallback(object : BaseTransientBottomBar.BaseCallback<BlockchainSnackbar>() {
                override fun onShown(transientBottomBar: BlockchainSnackbar) {
                    super.onShown(transientBottomBar)
                }

                override fun onDismissed(transientBottomBar: BlockchainSnackbar, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    doOnDismiss()
                }
            }).show()
        }
    }

    override fun fillPinBoxAtIndex(index: Int) {
        pinBoxList.getOrNull(index)?.setImageResource(R.drawable.rounded_view_dark_blue)
    }

    override fun clearPinBoxAtIndex(index: Int) {
        pinBoxList.getOrNull(index)?.setImageResource(R.drawable.rounded_view_blue_white_border)
    }

    override fun fillPinBoxes() {
        pinBoxList.forEach {
            it.setImageResource(R.drawable.rounded_view_dark_blue)
        }
    }

    override fun showProgressDialog(@StringRes messageId: Int) {
        dismissProgressDialog()
        materialProgressDialog = MaterialProgressDialog(requireContext()).apply {
            setCancelable(false)
            setMessage(getString(messageId))
        }
        if (isNotFinishing()) {
            materialProgressDialog?.show()
        }
    }

    override fun dismissProgressDialog() {
        if (materialProgressDialog?.isShowing == true) {
            materialProgressDialog?.dismiss()
        }
        materialProgressDialog = null
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        presenter.clearPinBoxes()
        presenter.checkFingerprintStatus()
    }

    override fun finishWithPayloadDecrypted() {
        val intent = Intent()
        activity?.setResult(RESULT_OK, intent)
        activity?.finish()
    }

    override fun finishWithResultOk(pin: String) {
        val bundle = Bundle()
        bundle.putString(KEY_VALIDATED_PIN, pin)
        val intent = Intent()
        intent.putExtras(bundle)
        activity?.setResult(RESULT_OK, intent)
        activity?.finish()
    }

    private fun finishWithResultCanceled() {
        val intent = Intent()
        activity?.setResult(RESULT_CANCELED, intent)
        activity?.finish()
    }

    override fun appNeedsUpgrade(isForced: Boolean) {
        if (context == null) return
        val appUpdateManager = AppUpdateManagerFactory.create(context)
        if (isForced) {
            compositeDisposable.add(
                updateInfo(appUpdateManager).subscribe { appUpdateInfoTask ->
                    if (canTriggerAnUpdateOfType(
                            AppUpdateType.IMMEDIATE,
                            appUpdateInfoTask
                        ) && activity != null
                    ) {
                        updateForcedNatively(appUpdateManager, appUpdateInfoTask.result)
                    } else {
                        handleForcedUpdateFromStore()
                    }
                }
            )
        } else {
            compositeDisposable.add(
                updateInfo(appUpdateManager).subscribe { appUpdateInfoTask ->
                    if (canTriggerAnUpdateOfType(
                            AppUpdateType.FLEXIBLE,
                            appUpdateInfoTask
                        ) && activity != null
                    ) {
                        updateFlexibleNatively(appUpdateManager, appUpdateInfoTask.result)
                    }
                }
            )
        }
    }

    private fun updateInfo(appUpdateManager: AppUpdateManager): Observable<Task<AppUpdateInfo>> {
        return Observable.fromCallable { appUpdateManager.appUpdateInfo }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun canTriggerAnUpdateOfType(
        updateAvailabilityType: Int,
        appUpdateInfoTask: Task<AppUpdateInfo>
    ): Boolean {
        return (
            appUpdateInfoTask.result.updateAvailability() ==
                UpdateAvailability.UPDATE_AVAILABLE ||
                appUpdateInfoTask.result.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) &&
            appUpdateInfoTask.result.isUpdateTypeAllowed(updateAvailabilityType)
    }

    private fun updateFlexibleNatively(
        appUpdateManager: AppUpdateManager,
        appUpdateInfo: AppUpdateInfo
    ) {
        val updatedListener = object : InstallStateUpdatedListener {
            override fun onStateUpdate(installState: InstallState) {
                if (installState.installStatus() == InstallStatus.DOWNLOADED) {
                    appUpdateManager.completeUpdate()
                }
                if (shouldBeUnregistered(installState.installStatus())) {
                    appUpdateManager.unregisterListener(this)
                }
            }
        }
        appUpdateManager.registerListener(updatedListener)
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            AppUpdateType.FLEXIBLE,
            activity,
            PinEntryActivity.REQUEST_CODE_UPDATE
        )
    }

    private fun shouldBeUnregistered(installStatus: Int): Boolean {
        return installStatus == InstallStatus.CANCELED ||
            installStatus == InstallStatus.DOWNLOADED ||
            installStatus == InstallStatus.INSTALLED ||
            installStatus == InstallStatus.FAILED
    }

    private fun updateForcedNatively(
        appUpdateManager: AppUpdateManager,
        appUpdateInfo: AppUpdateInfo
    ) {
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            AppUpdateType.IMMEDIATE,
            activity,
            PinEntryActivity.REQUEST_CODE_UPDATE
        )
    }

    private fun handleForcedUpdateFromStore() {
        val alertDialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.force_upgrade_message)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(R.string.exit, null)
            .setCancelable(false)
            .create()

        alertDialog.show()
        // Buttons are done this way to avoid dismissing the dialog
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val appPackageName = context?.packageName.orEmpty()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APP_STORE_URI + appPackageName)))
            } catch (e: ActivityNotFoundException) {
                // Device doesn't have the Play Store installed, direct them to the official
                // store web page anyway
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(APP_STORE_URL + appPackageName))
                )
            }
        }
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener { presenter.clearLoginState() }
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        dismissFingerprintDialog()
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
    }

    override fun onDestroy() {
        dismissProgressDialog()
        super.onDestroy()
        _binding = null
    }

    internal fun dismissFingerprintDialog() {
        // Hide if fingerprint unlock has become unavailable
        if (!presenter.ifShouldShowFingerprintLogin) {
            binding.fingerprintLogo.gone()
        }
    }

    private inner class ClearPinNumberRunnable : Runnable {
        override fun run() {
            pinBoxList.forEach {
                it.setImageResource(R.drawable.rounded_view_blue_white_border)
            }
        }
    }

    override fun showMobileNotice(mobileNoticeDialog: MobileNoticeDialog) {
        if (activity?.isFinishing == false && fragmentManager != null) {
            val alertFragment = MobileNoticeDialogFragment.newInstance(mobileNoticeDialog)
            alertFragment.show(requireFragmentManager(), alertFragment.tag)
        }
    }

    override fun restartAppWithVerifiedPin() {
        appUtil.loadAppWithVerifiedPin(LoaderActivity::class.java, isAfterWalletCreation)
    }

    override fun closePinChangeScreen() {
        activity?.finish()
    }

    override fun createPresenter(): PinEntryPresenter = pinEntryPresenter

    override fun setupCommitHashView() {
        binding.debugCommitHash.apply {
            visibleIf { BuildConfig.COMMIT_HASH.isNotEmpty() }
            text = BuildConfig.COMMIT_HASH
            copyHashOnLongClick(requireContext())
        }
    }

    override fun getMvpView(): PinEntryView = this

    companion object {
        private const val KEY_IS_AFTER_WALLET_CREATION = "is_after_wallet_creation"
        private val HANDLER = Handler()

        fun newInstance(
            isAfterCreateWallet: Boolean
        ): PinEntryFragment {
            val args = Bundle()
            args.putBoolean(KEY_IS_AFTER_WALLET_CREATION, isAfterCreateWallet)
            val fragment = PinEntryFragment()
            fragment.arguments = args
            return fragment
        }
    }
}

const val KEY_VALIDATING_PIN_FOR_RESULT = "validating_pin"
const val KEY_VALIDATING_PIN_FOR_RESULT_AND_PAYLOAD = "validating_pin_and_payload"
const val KEY_VALIDATED_PIN = "validated_pin"
const val REQUEST_CODE_VALIDATE_PIN = 88
