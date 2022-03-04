package piuk.blockchain.android.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_FIRST_USER
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.blockchain.biometrics.BiometricAuthError
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthLockout
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthLockoutPermanent
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthOther
import com.blockchain.biometrics.BiometricAuthError.BiometricKeysInvalidated
import com.blockchain.biometrics.BiometricAuthError.BiometricsNoSuitableMethods
import com.blockchain.biometrics.BiometricsCallback
import com.blockchain.biometrics.BiometricsType
import com.blockchain.commonarch.presentation.base.SlidingModalBottomDialog
import com.blockchain.commonarch.presentation.mvi.MviFragment.Companion.BOTTOM_SHEET
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.legacy.MaterialProgressDialog
import com.blockchain.componentlib.viewextensions.getAlertDialogPaddedView
import com.blockchain.componentlib.viewextensions.hideKeyboard
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.core.payments.LinkedPaymentMethod
import com.blockchain.core.payments.model.LinkBankTransfer
import com.blockchain.enviroment.Environment
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvent
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.LaunchOrigin
import com.blockchain.notifications.analytics.ProviderSpecificAnalytics
import com.blockchain.preferences.CurrencyPrefs
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.mukesh.countrypicker.CountryPicker
import info.blockchain.balance.FiatCurrency
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.util.PasswordUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import org.koin.android.ext.android.inject
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.R.string.success
import piuk.blockchain.android.cards.CardDetailsActivity
import piuk.blockchain.android.cards.RemoveCardBottomSheet
import piuk.blockchain.android.data.biometrics.BiometricPromptUtil
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.data.biometrics.WalletBiometricData
import piuk.blockchain.android.databinding.ModalChangePasswordBinding
import piuk.blockchain.android.scan.QrScanError
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import piuk.blockchain.android.simplebuy.linkBankEventWithCurrency
import piuk.blockchain.android.simplebuy.sheets.RemoveLinkedBankBottomSheet
import piuk.blockchain.android.ui.auth.KEY_VALIDATING_PIN_FOR_RESULT
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.auth.REQUEST_CODE_VALIDATE_PIN
import piuk.blockchain.android.ui.backup.BackupWalletActivity
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar
import piuk.blockchain.android.ui.customviews.PasswordStrengthView
import piuk.blockchain.android.ui.dashboard.model.LinkablePaymentMethodsForAction
import piuk.blockchain.android.ui.dashboard.sheets.LinkBankMethodChooserBottomSheet
import piuk.blockchain.android.ui.dashboard.sheets.WireTransferAccountDetailsBottomSheet
import piuk.blockchain.android.ui.kyc.limits.KycLimitsActivity
import piuk.blockchain.android.ui.linkbank.BankAuthActivity
import piuk.blockchain.android.ui.linkbank.BankAuthSource
import piuk.blockchain.android.ui.pairingcode.PairingBottomSheet
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.android.ui.settings.SettingsAnalytics.Companion.TWO_SET_MOBILE_NUMBER_OPTION
import piuk.blockchain.android.ui.settings.preferences.BankPreference
import piuk.blockchain.android.ui.settings.preferences.CardPreference
import piuk.blockchain.android.ui.settings.preferences.ThePitStatusPreference
import piuk.blockchain.android.ui.thepit.ExchangeConnectionSheet
import piuk.blockchain.android.ui.thepit.PitPermissionsActivity
import piuk.blockchain.android.urllinks.URL_PRIVACY_POLICY
import piuk.blockchain.android.urllinks.URL_TOS_POLICY
import piuk.blockchain.android.util.AfterTextChangedWatcher
import piuk.blockchain.android.util.AndroidUtils
import piuk.blockchain.android.util.FormatChecker
import piuk.blockchain.android.util.RootUtil
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber

class SettingsFragment :
    PreferenceFragmentCompat(),
    SettingsView,
    RemoveCardBottomSheet.Host,
    RemoveLinkedBankBottomSheet.Host,
    BankLinkingHost {

    // Profile
    private val guidPref by lazy {
        findPreference<Preference>("guid")
    }
    private val emailPref by lazy {
        findPreference<Preference>("email")
    }
    private val smsPref by lazy {
        findPreference<Preference>("mobile")
    }
    private val thePit by lazy {
        findPreference<ThePitStatusPreference>("the_pit")
    }
    private val limitsPref by lazy {
        findPreference<Preference>("limits")
    }
    private val qrConnectPref by lazy {
        findPreference<Preference>("qr_connect")
    }
    private val banksPref by lazy {
        findPreference<PreferenceCategory>("banks")
    }
    private val cardsPref by lazy {
        findPreference<PreferenceCategory>("cards")
    }

    // Preferences
    private val fiatPref by lazy {
        findPreference<Preference>("fiat")
    }
    private val emailNotificationPref by lazy {
        findPreference<Preference>("email_notifications") as SwitchPreferenceCompat
    }
    private val pushNotificationPref by lazy {
        findPreference<Preference>("push_notifications") as SwitchPreferenceCompat
    }

    // Security
    private val fingerprintPref by lazy {
        findPreference<Preference>("fingerprint") as SwitchPreferenceCompat
    }
    private val twoStepVerificationPref by lazy {
        findPreference<SwitchPreferenceCompat>("2fa")
    }
    private val torPref by lazy {
        findPreference<SwitchPreferenceCompat>("tor")
    }
    private val launcherShortcutPrefs by lazy {
        findPreference<SwitchPreferenceCompat>("receive_shortcuts_enabled")
    }
    private val screenshotPref by lazy {
        findPreference<SwitchPreferenceCompat>("screenshots_enabled")
    }
    private val cloudBackupPref by lazy {
        findPreference<SwitchPreferenceCompat>("cloud_backup")
    }

    private val settingsPresenter: SettingsPresenter by scopedInject()
    private val biometricsController: BiometricsController by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val analytics: Analytics by inject()
    private val specificAnalytics: ProviderSpecificAnalytics by inject()
    private val rxBus: RxBus by inject()
    private val formatChecker: FormatChecker by inject()
    private val environmentConfig: EnvironmentConfig by inject()

    private var progressDialog: MaterialProgressDialog? = null

    private val reviewManager: ReviewManager by lazy {
        ReviewManagerFactory.create(activity)
    }

    private var reviewInfo: ReviewInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsPresenter.initView(this)
        settingsPresenter.onViewReady()

        analytics.logEvent(AnalyticsEvents.Settings)
        specificAnalytics.logContentView(javaClass.simpleName)
        initReviews()
    }

    private fun initReviews() {
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                analytics.logEvent(ReviewAnalytics.REQUEST_REVIEW_SUCCESS)
                reviewInfo = request.result
            } else {
                analytics.logEvent(ReviewAnalytics.REQUEST_REVIEW_FAILURE)
            }
        }
    }

    override fun setUpUi() {
        // Profile
        guidPref.onClick {
            showDialogGuid()
            analytics.logEvent(SettingsAnalytics.WalletIdCopyClicked)
        }
        emailPref.onClick {
            onUpdateEmailClicked()
            analytics.logEvent(SettingsAnalytics.EmailClicked)
        }
        smsPref.onClick {
            settingsPresenter.onVerifySmsRequested()
            analytics.logEvent(SettingsAnalytics.PhoneClicked)
            analytics.logEvent(SettingsAnalytics.MobileChangeClicked)
        }

        thePit.onClick { settingsPresenter.onThePitClicked() }
        thePit?.isVisible = true

        limitsPref.onClick {
            startActivity(Intent(requireContext(), KycLimitsActivity::class.java))
            analytics.logEvent(SettingsAnalytics.LimitsAndFeaturesClicked)
        }

        qrConnectPref?.isVisible = environmentConfig.isRunningInDebugMode() &&
            environmentConfig.environment != Environment.PRODUCTION
        qrConnectPref.onClick { PairingBottomSheet().show(childFragmentManager, BOTTOM_SHEET) }

        // Preferences
        fiatPref.onClick { showDialogFiatUnits() }
        emailNotificationPref.onClick {
            showDialogEmailNotifications()
            analytics.logEvent(SettingsAnalytics.EmailNotificationClicked)
        }
        pushNotificationPref.onClick {
            showDialogPushNotifications()
            analytics.logEvent(SettingsAnalytics.NotificationPrefsUpdated)
        }

        // Security
        fingerprintPref.onClick {
            onFingerprintClicked()
            analytics.logEvent(SettingsAnalytics.BiometryAuthSwitch)
        }

        findPreference<Preference>("pin").onClick {
            showDialogChangePin()
            analytics.logEvent(SettingsAnalytics.ChangePinClicked_Old)
            analytics.logEvent(SettingsAnalytics.ChangePinClicked)
        }
        twoStepVerificationPref.onClick {
            settingsPresenter.onTwoStepVerificationRequested()
            analytics.logEvent(SettingsAnalytics.TwoFactorAuthClicked)
            analytics.logEvent(SettingsAnalytics.TwoStepVerificationClicked(TWO_SET_MOBILE_NUMBER_OPTION))
        }

        findPreference<Preference>("change_pw").onClick {
            showDialogChangePasswordWarning()
            analytics.logEvent(SettingsAnalytics.ChangePassClicked)
        }

        torPref?.setOnPreferenceChangeListener { _, newValue ->
            settingsPresenter.updateTor(newValue as Boolean)
            true
        }

        findPreference<Preference>("backup")?.apply {
            onClick { onBackupClicked() }
        }

        screenshotPref?.setOnPreferenceChangeListener { _, newValue ->
            settingsPresenter.updateScreenshotPreference(newValue as Boolean)
            true
        }

        launcherShortcutPrefs?.setOnPreferenceChangeListener { _, newValue ->
            if (!(newValue as Boolean) && AndroidUtils.is25orHigher()) {
                settingsActivity.getSystemService(
                    ShortcutManager::class.java
                )!!.removeAllDynamicShortcuts()
            }
            true
        }

        cloudBackupPref?.setOnPreferenceChangeListener { _, newValue ->
            settingsPresenter.updateCloudData(newValue as Boolean)
            analytics.logEvent(SettingsAnalytics.CloudBackupSwitch)
            true
        }

        // App
        findPreference<Preference>("about")?.apply {
            summary = getString(
                R.string.about,
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.COMMIT_HASH}",
                Calendar.getInstance().get(Calendar.YEAR).toString()
            )
        }

        findPreference<Preference>("tos").onClick {
            onTosClicked()
        }
        findPreference<Preference>("privacy").onClick {
            onPrivacyClicked()
        }

        settingsPresenter.checkShouldDisplayRateUs()

        val disableRootWarningPref = findPreference<Preference>(PersistentPrefs.KEY_ROOT_WARNING_DISABLED)
        if (disableRootWarningPref != null && !RootUtil().isDeviceRooted) {
            val appCategory = findPreference<Preference>("app") as PreferenceCategory
            appCategory.removePreference(disableRootWarningPref)
        }

        // Check if referred from Security Centre dialog
        val intent = activity?.intent
        when {
            intent == null -> {
            }
            intent.hasExtra(EXTRA_SHOW_TWO_FA_DIALOG) ->
                settingsPresenter.onTwoStepVerificationRequested()
            intent.hasExtra(EXTRA_SHOW_ADD_EMAIL_DIALOG) ->
                settingsPresenter.onEmailShowRequested()
        }
    }

    override fun showRateUsPreference() {
        findPreference<Preference>("rate_app")?.apply {
            isVisible = true
            onClick {
                reviewInfo?.let {
                    reviewManager.launchReviewFlow(activity, reviewInfo).addOnFailureListener {
                        analytics.logEvent(ReviewAnalytics.LAUNCH_REVIEW_FAILURE)
                        goToPlayStore()
                    }.addOnCompleteListener {
                        analytics.logEvent(ReviewAnalytics.LAUNCH_REVIEW_SUCCESS)
                    }
                } ?: goToPlayStore()
            }
        }
    }

    override fun showProgress() {
        progressDialog = MaterialProgressDialog(requireContext()).apply {
            setCancelable(false)
            setMessage(R.string.please_wait)
            show()
        }
    }

    override fun showEmailDialog(currentEmail: String, emailVerified: Boolean) {
        showUpdateEmailDialog(settingsActivity, settingsPresenter, currentEmail, emailVerified, formatChecker)
    }

    override fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun showScanTargetError(error: QrScanError) {
        showSnackbar(
            when (error.errorCode) {
                QrScanError.ErrorCode.ScanFailed -> R.string.error_scan_failed_general
                QrScanError.ErrorCode.BitPayScanFailed -> R.string.error_scan_failed_bitpay
            },
            SnackbarType.Error
        )
    }

    private fun goToPlayStore() {
        val flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        try {
            val appPackageName = requireActivity().packageName
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$appPackageName")
            ).let {
                it.addFlags(flags)
                startActivity(it)
            }
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Google Play Store not found")
        }
    }

    override fun showWarningDialog(@StringRes message: Int) {
        activity?.let {
            AlertDialog.Builder(it, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { showDialogVerifySms() }
                .create()
                .show()
        }
    }

    private val compositeDisposable = CompositeDisposable()

    private val event by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable += event.subscribe {
            settingsPresenter.onViewReady()
        }
    }

    override fun onPause() {
        rxBus.unregister(ActionEvent::class.java, event)
        compositeDisposable.clear()
        super.onPause()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        preferenceScreen?.removeAll()
        addPreferencesFromResource(R.xml.settings)
    }

    override fun setGuidSummary(summary: String) {
        guidPref?.summary = summary
    }

    override fun setEmailSummary(email: String, isVerified: Boolean) {
        emailPref?.summary = when {
            email.isEmpty() -> getString(R.string.not_specified)
            isVerified -> "$email  (${getString(R.string.verified)})"
            else -> "$email  (${getString(R.string.unverified)})"
        }
    }

    override fun setEmailUnknown() {
        emailPref?.summary = getString(R.string.not_specified)
    }

    override fun setSmsSummary(smsNumber: String, isVerified: Boolean) {
        smsPref?.summary = when {
            smsNumber.isEmpty() -> getString(R.string.not_specified)
            isVerified -> "$smsNumber (${getString(R.string.verified)})"
            else -> "$smsNumber (${getString(R.string.unverified)})"
        }
    }

    override fun setSmsUnknown() {
        emailPref?.summary = getString(R.string.not_specified)
    }

    override fun setFiatSummary(summary: String) {
        fiatPref?.summary = summary
    }

    override fun setEmailNotificationsVisibility(visible: Boolean) {
        emailNotificationPref.isVisible = visible
    }

    override fun setEmailNotificationPref(enabled: Boolean) {
        emailNotificationPref.isChecked = enabled
    }

    override fun setPushNotificationPref(enabled: Boolean) {
        pushNotificationPref.isChecked = enabled
    }

    override fun setFingerprintVisibility(visible: Boolean) {
        fingerprintPref.isVisible = visible
    }

    override fun setTwoFaPreference(enabled: Boolean) {
        twoStepVerificationPref?.isChecked = enabled
    }

    override fun setTorBlocked(blocked: Boolean) {
        torPref?.isChecked = blocked
    }

    override fun setPitLinkingState(isLinked: Boolean) {
        thePit?.setValue(isLinked)
    }

    override fun updateLinkNewBank(linkablePaymentMethods: LinkablePaymentMethods) {
        val enabled = linkablePaymentMethods.linkMethods.isNotEmpty()
        val addBankPref = banksPref?.findPreference<BankPreference>(LINK_BANK_KEY)
        if (enabled && addBankPref == null) {
            banksPref?.addPreference(
                BankPreference(
                    context = requireContext(),
                    fiatCurrency = linkablePaymentMethods.currency.displayTicker
                ).apply {
                    onClick {
                        linkBank(linkablePaymentMethods)
                    }
                    key = LINK_BANK_KEY
                }
            )
        } else if (!enabled && addBankPref != null) {
            banksPref?.removePreference(addBankPref)
        }
    }

    override fun updateLinkedBanks(bankItems: List<BankItem>) {
        val existingBanks = prefsExistingBanks()

        val newBankItems = bankItems.filterNot { existingBanks.contains(it.bank.id) }

        newBankItems.forEach { bankItem ->
            banksPref?.addPreference(
                BankPreference(
                    context = requireContext(), bankItem = bankItem, fiatCurrency = bankItem.bank.currency.displayTicker
                ).apply {
                    onClick {
                        removeBank(bankItem.bank)
                    }
                    key = bankItem.bank.id
                }
            )
        }
    }

    private fun removeBank(bank: LinkedPaymentMethod.Bank) {
        RemoveLinkedBankBottomSheet.newInstance(bank).show(childFragmentManager, BOTTOM_SHEET)
    }

    override fun onBankWireTransferSelected(currency: FiatCurrency) {
        WireTransferAccountDetailsBottomSheet.newInstance(currency).show(childFragmentManager, BOTTOM_SHEET)
        analytics.logEvent(linkBankEventWithCurrency(SimpleBuyAnalytics.WIRE_TRANSFER_CLICKED, currency.networkTicker))
    }

    private fun linkBank(linkablePaymentMethods: LinkablePaymentMethods) {
        require(linkablePaymentMethods.linkMethods.isNotEmpty())
        if (linkablePaymentMethods.linkMethods.size > 1) {
            showDialogForLinkBankMethodChooser(linkablePaymentMethods)
        } else {
            when (linkablePaymentMethods.linkMethods[0]) {
                PaymentMethodType.BANK_ACCOUNT -> onBankWireTransferSelected(linkablePaymentMethods.currency)
                PaymentMethodType.BANK_TRANSFER ->
                    onLinkBankSelected(
                        LinkablePaymentMethodsForAction.LinkablePaymentMethodsForSettings(linkablePaymentMethods)
                    )
                else -> throw IllegalStateException("Not valid linkable bank type")
            }
        }
    }

    override fun onLinkBankSelected(paymentMethodForAction: LinkablePaymentMethodsForAction) {
        settingsPresenter.linkBank(paymentMethodForAction.linkablePaymentMethods.currency)
    }

    private fun showDialogForLinkBankMethodChooser(linkablePaymentMethods: LinkablePaymentMethods) {
        LinkBankMethodChooserBottomSheet.newInstance(
            LinkablePaymentMethodsForAction.LinkablePaymentMethodsForSettings(
                linkablePaymentMethods
            )
        )
            .show(childFragmentManager, BOTTOM_SHEET)
    }

    override fun updateCards(cards: List<PaymentMethod.Card>) {
        val existingCards = prefsExistingCards()

        val newCards = cards.filterNot { existingCards.contains(it.cardId) }

        newCards.forEach { card ->
            cardsPref?.addPreference(
                CardPreference(context = requireContext(), card = card).apply {
                    onClick {
                        RemoveCardBottomSheet.newInstance(card).show(childFragmentManager, BOTTOM_SHEET)
                    }
                    key = card.cardId
                }
            )
        }

        cardsPref?.findPreference<CardPreference>(ADD_CARD_KEY)?.let {
            it.order = it.order + newCards.size + 1
        }
    }

    private fun prefsExistingCards(): List<String> {
        val existingCards = mutableListOf<String>()

        for (i in (0 until (cardsPref?.preferenceCount ?: 0))) {
            existingCards.add(cardsPref?.getPreference(i)?.key.takeIf { it != ADD_CARD_KEY } ?: continue)
        }
        return existingCards
    }

    private fun addNewCard() {
        val intent = Intent(activity, CardDetailsActivity::class.java)
        startActivityForResult(intent, CardDetailsActivity.ADD_CARD_REQUEST_CODE)
        analytics.logEvent(SimpleBuyAnalytics.SETTINGS_ADD_CARD)
    }

    override fun setScreenshotsEnabled(enabled: Boolean) {
        screenshotPref?.isChecked = enabled
    }

    private fun prefsExistingBanks(): List<String> {
        val existingBanks = mutableListOf<String>()

        for (i in (0 until (banksPref?.preferenceCount ?: 0))) {
            existingBanks.add(
                banksPref?.getPreference(i)?.key.takeIf {
                    it?.contains(LINK_BANK_KEY)?.not() ?: false
                } ?: continue
            )
        }
        return existingBanks
    }

    override fun setLauncherShortcutVisibility(visible: Boolean) {
        launcherShortcutPrefs?.isVisible = visible
    }

    private fun onFingerprintClicked() {
        settingsPresenter.onFingerprintClicked()
    }

    override fun showDisableFingerprintDialog() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.biometric_disable_message)
            .setCancelable(true)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                settingsPresenter.setFingerprintUnlockDisabled()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                updateFingerprintPreferenceStatus()
            }
            .show()
    }

    override fun showNoFingerprintsAddedDialog() {
        updateFingerprintPreferenceStatus()
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.fingerprint_no_fingerprints_added)
            .setCancelable(true)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                startActivityForResult(
                    Intent(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            android.provider.Settings.ACTION_SECURITY_SETTINGS
                        } else {
                            android.provider.Settings.ACTION_BIOMETRIC_ENROLL
                        }
                    ),
                    REQUEST_CODE_BIOMETRIC_ENROLLMENT
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun updateFingerprintPreferenceStatus() {
        fingerprintPref.isChecked = settingsPresenter.isFingerprintUnlockEnabled
        analytics.logEvent(SettingsAnalytics.BiometricsOptionUpdated(fingerprintPref.isChecked))
    }

    override fun showFingerprintDialog(pincode: String) {
        biometricsController.authenticate(
            this, BiometricsType.TYPE_REGISTER,
            object : BiometricsCallback<WalletBiometricData> {
                override fun onAuthSuccess(data: WalletBiometricData) {
                    updateFingerprintPreferenceStatus()
                }

                override fun onAuthFailed(error: BiometricAuthError) {
                    handleAuthFailed(error)
                }

                override fun onAuthCancelled() {
                    settingsPresenter.setFingerprintUnlockDisabled()
                    updateFingerprintPreferenceStatus()
                }
            }
        )
    }

    private fun handleAuthFailed(error: BiometricAuthError) {
        when (error) {
            is BiometricKeysInvalidated -> BiometricPromptUtil.showActionableInvalidatedKeysDialog(
                requireContext(),
                positiveActionCallback = {
                    settingsPresenter.onFingerprintClicked()
                },
                negativeActionCallback = {
                    startActivityForResult(
                        Intent(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                                android.provider.Settings.ACTION_SECURITY_SETTINGS
                            } else {
                                android.provider.Settings.ACTION_BIOMETRIC_ENROLL
                            }
                        ),
                        REQUEST_CODE_BIOMETRIC_ENROLLMENT
                    )
                }
            )
            is BiometricsNoSuitableMethods -> showNoFingerprintsAddedDialog()
            is BiometricAuthLockout -> BiometricPromptUtil.showAuthLockoutDialog(requireContext())
            is BiometricAuthLockoutPermanent -> BiometricPromptUtil.showPermanentAuthLockoutDialog(requireContext())
            is BiometricAuthOther -> BiometricPromptUtil.showBiometricsGenericError(requireContext(), error.error)
            else -> {
                // do nothing
            }
        }
        settingsPresenter.setFingerprintUnlockDisabled()
        updateFingerprintPreferenceStatus()
    }

    override fun showDialogSmsVerified() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(success)
            .setMessage(R.string.sms_verified)
            .setPositiveButton(R.string.dialog_continue) { _, _ -> settingsPresenter.onTwoStepVerificationRequested() }
            .show()
    }

    override fun goToPinEntryPage() {
        PinEntryActivity.startPinChange(requireContext())
        analytics.logEvent(SettingsAnalytics.PinChanged_Old)
        analytics.logEvent(SettingsAnalytics.PinCodeChanged)
    }

    override fun launchThePitLandingActivity() {
        PitPermissionsActivity.start(requireActivity(), "")
    }

    override fun launchThePit() {
        ExchangeConnectionSheet.launch(requireActivity())
    }

    private fun onUpdateEmailClicked() {
        settingsPresenter.onEmailShowRequested()
        analytics.logEvent(SettingsAnalytics.EmailChangeClicked)
    }

    private fun onBackupClicked() {
        BackupWalletActivity.start(requireContext())
    }

    private fun onTosClicked() {
        analytics.logEvent(
            SettingsAnalytics.SettingsHyperlinkClicked(
                SettingsAnalytics.AnalyticsHyperlinkDestination.TERMS_OF_SERVICE
            )
        )
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_TOS_POLICY)))
    }

    private fun onPrivacyClicked() {
        analytics.logEvent(
            SettingsAnalytics.SettingsHyperlinkClicked(
                SettingsAnalytics.AnalyticsHyperlinkDestination.PRIVACY_POLICY
            )
        )
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_PRIVACY_POLICY)))
    }

    override fun showDialogEmailVerification() {
        // Slight delay to prevent UI blinking issues
        val handler = Handler()
        handler.postDelayed({
            if (activity != null) {
                AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                    .setTitle(R.string.verify)
                    .setMessage(R.string.verify_email_notice)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }, 300)
    }

    override fun showDialogMobile(authType: Int, isSmsVerified: Boolean, smsNumber: String) {
        if (authType != Settings.AUTH_TYPE_OFF) {
            AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setMessage(R.string.disable_2fa_first)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show()
        } else {
            val inflater = settingsActivity.layoutInflater
            val smsPickerView = inflater.inflate(R.layout.include_sms_update, null)
            val mobileNumber = smsPickerView.findViewById<EditText>(R.id.etSms)
            val countryTextView = smsPickerView.findViewById<TextView>(R.id.tvCountry)
            val mobileNumberTextView = smsPickerView.findViewById<TextView>(R.id.tvSms)

            val picker = CountryPicker.Builder()
                .with(requireActivity())
                .listener { country ->
                    setCountryFlag(
                        countryTextView, country.dialCode, country.flag
                    )
                }
                .theme(CountryPicker.THEME_NEW)
                .build()

            val country =
                picker.countryFromSIM
                    ?: picker.getCountryByLocale(Locale.getDefault())
                    ?: picker.getCountryByISO("US")
            if (country.dialCode == "+93") {
                setCountryFlag(countryTextView, "+1", R.drawable.flag_us)
            } else {
                setCountryFlag(countryTextView, country.dialCode, country.flag)
            }

            countryTextView.setOnClickListener {
                picker.showBottomSheet(requireActivity() as AppCompatActivity)
            }

            if (smsNumber.isNotEmpty()) {
                mobileNumberTextView.text = smsNumber
                mobileNumberTextView.visibility = View.VISIBLE
            }

            val alertDialogSmsBuilder = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.mobile)
                .setMessage(getString(R.string.mobile_description))
                .setView(smsPickerView)
                .setCancelable(false)
                .setPositiveButton(R.string.update, null)
                .setNegativeButton(android.R.string.cancel, null)

            if (isSmsVerified && smsNumber.isNotEmpty()) {
                alertDialogSmsBuilder.setNeutralButton(R.string.verify) { _, _ ->
                    settingsPresenter.updateSms(
                        smsNumber
                    )
                }
            }

            val dialog = alertDialogSmsBuilder.create()
            dialog.setOnShowListener {
                val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                positive.setOnClickListener {
                    val sms = countryTextView.text.toString() + mobileNumber.text.toString()

                    if (!formatChecker.isValidMobileNumber(sms)) {
                        showSnackbar(R.string.invalid_mobile, SnackbarType.Error)
                    } else {
                        settingsPresenter.updateSms(sms)
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
        }
    }

    private fun showDialogGuid() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.app_name)
            .setMessage(R.string.guid_to_clipboard)
            .setCancelable(false)
            .setPositiveButton(R.string.common_yes) { _, _ ->
                val clipboard =
                    settingsActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("guid", guidPref!!.summary)
                clipboard.setPrimaryClip(clip)
                showSnackbar(R.string.copied_to_clipboard, SnackbarType.Success)
                analytics.logEvent(SettingsAnalytics.WalletIdCopyCopied)
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
    }

    private fun showDialogFiatUnits() {
        val currencies = settingsPresenter.currencyLabels
        val selectedCurrency = currencyPrefs.selectedFiatCurrency
        var selected = 0
        for (i in currencies.indices) {
            if (currencies[i] == selectedCurrency) {
                selected = i
                break
            }
        }

        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.select_currency)
            .setSingleChoiceItems(currencies.map { it.displayTicker }.toTypedArray(), selected) { dialog, which ->
                settingsPresenter.updateFiatUnit(currencies[which])
                dialog.dismiss()
            }
            .show()
    }

    private val settingsActivity: Activity
        get() = activity ?: throw IllegalStateException("Activity not found")

    override fun showDialogVerifySms() {
        val editText = AppCompatEditText(settingsActivity)
        editText.isSingleLine = true

        val dialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.verify_mobile)
            .setMessage(R.string.verify_sms_summary)
            .setView(requireContext().getAlertDialogPaddedView(editText))
            .setCancelable(false)
            .setPositiveButton(R.string.verify, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.resend) { _, _ ->
                settingsPresenter.resendSms()
            }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val codeS = editText.text.toString()
                if (codeS.isNotEmpty()) {
                    settingsPresenter.verifySms(codeS)
                    dialog.dismiss()
                    settingsActivity.hideKeyboard()
                }
            }
        }
        dialog.show()
    }

    private fun showDialogChangePin() {
        val intent = Intent(activity, PinEntryActivity::class.java)
        intent.putExtra(PinEntryActivity.KEY_ORIGIN_SETTINGS, true)
        intent.putExtra(KEY_VALIDATING_PIN_FOR_RESULT, true)
        startActivityForResult(intent, REQUEST_CODE_VALIDATE_PIN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_VALIDATE_PIN -> {
                    settingsPresenter.pinCodeValidatedForChange()
                }
                CardDetailsActivity.ADD_CARD_REQUEST_CODE -> {
                    updateCards(
                        listOf(
                            (data?.extras?.getSerializable(CardDetailsActivity.CARD_KEY) as? PaymentMethod.Card)
                                ?: return
                        )
                    )
                    settingsPresenter.updateCanAddNewCard()
                }
                BankAuthActivity.LINK_BANK_REQUEST_CODE -> {
                    settingsPresenter.updateBanks()
                }
                REQUEST_CODE_BIOMETRIC_ENROLLMENT -> {
                    settingsPresenter.onFingerprintClicked()
                }
                QrScanActivity.SCAN_URI_RESULT -> {
                    data.getRawScanData()?.let { scanData ->
                        settingsPresenter.processScanResult(scanData)
                    }
                }
            }
        } else if (resultCode == RESULT_FIRST_USER || resultCode == RESULT_CANCELED) {
            if (requestCode == REQUEST_CODE_BIOMETRIC_ENROLLMENT) {
                settingsPresenter.onFingerprintClicked()
            }
        }
    }

    private fun showDialogEmailNotifications() {
        val dialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.email_notifications)
            .setMessage(R.string.email_notifications_summary)
            .setPositiveButton(R.string.enable) { _, _ ->
                settingsPresenter.updateEmailNotification(
                    true
                )
            }
            .setNegativeButton(R.string.disable) { _, _ ->
                settingsPresenter.updateEmailNotification(
                    false
                )
            }
            .create()

        dialog.setOnCancelListener {
            emailNotificationPref.isChecked = !emailNotificationPref.isChecked
        }
        dialog.show()
    }

    private fun showDialogPushNotifications() {
        val dialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.push_notifications)
            .setMessage(R.string.push_notifications_summary)
            .setPositiveButton(R.string.enable) { _, _ -> settingsPresenter.enablePushNotifications() }
            .setNegativeButton(R.string.disable) { _, _ -> settingsPresenter.disablePushNotifications() }
            .create()

        dialog.setOnCancelListener {
            pushNotificationPref.isChecked = !pushNotificationPref.isChecked
        }
        dialog.show()
    }

    private fun showDialogChangePasswordWarning() {
        AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
            .setTitle(R.string.warning)
            .setMessage(R.string.change_password_summary)
            .setPositiveButton(R.string.dialog_continue) { _, _ -> showDialogChangePassword() }
            .show()
    }

    private fun showDialogChangePassword() {
        val changePasswordBinding = ModalChangePasswordBinding.inflate(
            LayoutInflater.from(settingsActivity.layoutInflater.context), null, false
        )
        with(changePasswordBinding) {

            currentPassword.requestFocus()
            newPassword.addTextChangedListener(object : AfterTextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    newPassword.postDelayed({
                        if (activity != null && !settingsActivity.isFinishing) {
                            passwordStrength.visible()
                            setPasswordStrength(editable.toString(), passwordStrength)
                        }
                    }, 200)
                }
            })

            val alertDialog = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.change_password)
                .setCancelable(false)
                .setView(root)
                .setPositiveButton(R.string.update, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

            alertDialog.setOnShowListener {
                val buttonPositive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                buttonPositive.setOnClickListener {
                    val currentPw = currentPassword.text.toString()
                    val newPw = newPassword.text.toString()
                    val newConfirmedPw = confirmPassword.text.toString()
                    val walletPassword = settingsPresenter.tempPassword

                    if (currentPw != newPw) {
                        if (currentPw == walletPassword) {
                            if (newPw == newConfirmedPw) {
                                if (newConfirmedPw.length < 4 || newConfirmedPw.length > 255) {
                                    showSnackbar(R.string.invalid_password, SnackbarType.Error)
                                } else if (PasswordUtil.getStrength(newPw).roundToInt() < 50) {
                                    AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                                        .setTitle(R.string.app_name)
                                        .setMessage(R.string.weak_password)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.common_yes) { _, _ ->
                                            confirmPassword.setText("")
                                            confirmPassword.requestFocus()
                                            newPassword.setText("")
                                            newPassword.requestFocus()
                                        }
                                        .setNegativeButton(R.string.polite_no) { _, _ ->
                                            alertDialog.dismiss()
                                            settingsPresenter.updatePassword(
                                                newConfirmedPw,
                                                walletPassword
                                            )
                                        }
                                        .show()
                                } else {
                                    alertDialog.dismiss()
                                    settingsPresenter.updatePassword(newConfirmedPw, walletPassword)
                                }
                            } else {
                                confirmPassword.setText("")
                                confirmPassword.requestFocus()
                                showSnackbar(R.string.password_mismatch_error, SnackbarType.Error)
                            }
                        } else {
                            currentPassword.setText("")
                            currentPassword.requestFocus()
                            showSnackbar(R.string.invalid_password, SnackbarType.Error)
                        }
                    } else {
                        newPassword.setText("")
                        confirmPassword.setText("")
                        newPassword.requestFocus()
                        showSnackbar(R.string.change_password_new_matches_current, SnackbarType.Error)
                    }
                }
            }
            alertDialog.show()
        }
    }

    override fun showSnackbar(@StringRes stringId: Int, type: SnackbarType) {
        BlockchainSnackbar.make(
            this.requireView(),
            getString(stringId),
            type = type
        ).show()
    }

    override fun showDialogTwoFA(authType: Int, smsVerified: Boolean) {
        if (authType == Settings.AUTH_TYPE_GOOGLE_AUTHENTICATOR ||
            authType == Settings.AUTH_TYPE_YUBI_KEY
        ) {
            twoStepVerificationPref?.isChecked = true
            AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.warning)
                .setCancelable(false)
                .setMessage(R.string.disable_online_only)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show()
        } else if (!smsVerified) {
            twoStepVerificationPref?.isChecked = false
            settingsPresenter.onVerifySmsRequested()
        } else {
            val message = Html.fromHtml(getString(R.string.two_fa_description, URL_LOGIN))
            val spannable = SpannableString(message)
            Linkify.addLinks(spannable, Linkify.WEB_URLS)

            val alertDialogBuilder = AlertDialog.Builder(settingsActivity, R.style.AlertDialogStyle)
                .setTitle(R.string.two_fa)
                .setCancelable(false)
                .setMessage(spannable)
                .setNeutralButton(android.R.string.cancel) { _, _ ->
                    twoStepVerificationPref?.isChecked =
                        authType != Settings.AUTH_TYPE_OFF
                }

            if (authType != Settings.AUTH_TYPE_OFF) {
                alertDialogBuilder.setNegativeButton(R.string.disable) { _, _ ->
                    settingsPresenter.updateTwoFa(
                        Settings.AUTH_TYPE_OFF
                    )
                }
            } else {
                alertDialogBuilder.setPositiveButton(R.string.enable) { _, _ ->
                    settingsPresenter.updateTwoFa(
                        Settings.AUTH_TYPE_SMS
                    )
                }
            }
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
            (alertDialog.findViewById<View>(android.R.id.message) as TextView).movementMethod =
                LinkMovementMethod.getInstance()
        }
    }

    private fun setPasswordStrength(
        passwordEntered: String,
        passwordStrengthView: PasswordStrengthView
    ) {
        if (activity != null && !settingsActivity.isFinishing) {
            passwordStrengthView.updatePassword(passwordEntered)
        }
    }

    private fun setCountryFlag(tvCountry: TextView, dialCode: String, flagResourceId: Int) {
        tvCountry.text = dialCode
        val drawable = ContextCompat.getDrawable(settingsActivity, flagResourceId)
        drawable!!.alpha = 30
        tvCountry.background = drawable
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgress()
        settingsPresenter.onViewDestroyed()
    }

    companion object {
        const val URL_LOGIN = "<a href=\"https://login.blockchain.com/\">login.blockchain.com</a>"
        internal const val EXTRA_SHOW_ADD_EMAIL_DIALOG = "show_add_email_dialog"
        internal const val EXTRA_SHOW_TWO_FA_DIALOG = "show_two_fa_dialog"
        private const val ADD_CARD_KEY = "ADD_CARD_KEY"
        private const val LINK_BANK_KEY = "ADD_BANK_KEY"
        private const val REQUEST_CODE_BIOMETRIC_ENROLLMENT = 666

        fun newInstance(): SettingsFragment = SettingsFragment()
    }

    override fun onCardRemoved(cardId: String) {
        cardsPref?.findPreference<CardPreference>(cardId)?.let {
            cardsPref?.removePreference(it)
        }
        settingsPresenter.updateCanAddNewCard()
    }

    override fun onLinkedBankRemoved(bankId: String) {
        banksPref?.findPreference<BankPreference>(bankId)?.let {
            banksPref?.removePreference(it)
        }
    }

    override fun onSheetClosed() {}

    override fun cardsEnabled(enabled: Boolean) {
        cardsPref?.isVisible = enabled
    }

    override fun addCardEnabled(enabled: Boolean) {
        val addCardPref = cardsPref?.findPreference<CardPreference>(ADD_CARD_KEY)
        if (enabled && addCardPref == null) {
            cardsPref?.addPreference(
                CardPreference(context = requireContext()).apply {
                    onClick {
                        addNewCard()
                        analytics.logEvent(SettingsAnalytics.LinkCardClicked(LaunchOrigin.SETTINGS))
                    }
                    key = ADD_CARD_KEY
                }
            )
        } else if (!enabled && addCardPref != null) {
            cardsPref?.removePreference(addCardPref)
        }
    }

    override fun banksEnabled(enabled: Boolean) {
        banksPref?.isVisible = enabled
    }

    override fun linkBankWithPartner(linkBankTransfer: LinkBankTransfer) {
        startActivityForResult(
            BankAuthActivity.newInstance(
                linkBankTransfer,
                BankAuthSource.SETTINGS,
                requireContext()
            ),
            BankAuthActivity.LINK_BANK_REQUEST_CODE
        )
    }
}

fun Preference?.onClick(onClick: () -> Unit) {
    this?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
        onClick()
        true
    }
}

enum class ReviewAnalytics : AnalyticsEvent {
    REQUEST_REVIEW_SUCCESS,
    REQUEST_REVIEW_FAILURE,
    LAUNCH_REVIEW_SUCCESS,
    LAUNCH_REVIEW_FAILURE;

    override val event: String
        get() = name.toLowerCase(Locale.ENGLISH)
    override val params: Map<String, String>
        get() = emptyMap()
}

interface BankLinkingHost : SlidingModalBottomDialog.Host {
    fun onBankWireTransferSelected(currency: FiatCurrency)
    fun onLinkBankSelected(paymentMethodForAction: LinkablePaymentMethodsForAction)
}
