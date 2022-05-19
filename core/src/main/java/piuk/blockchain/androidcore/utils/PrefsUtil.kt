package piuk.blockchain.androidcore.utils

import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.preferences.AppInfoPrefs
import com.blockchain.preferences.Authorization
import com.blockchain.preferences.BrowserIdentity
import com.blockchain.preferences.BrowserIdentityMapping
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.FiatCurrency
import info.blockchain.wallet.crypto.AESUtil
import java.util.concurrent.TimeUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.spongycastle.util.encoders.Hex
import piuk.blockchain.androidcore.utils.PersistentPrefs.Companion.KEY_EMAIL_VERIFIED

interface UUIDGenerator {
    fun generateUUID(): String
}

class PrefsUtil(
    private val ctx: Context,
    private val store: SharedPreferences,
    private val backupStore: SharedPreferences,
    private val idGenerator: DeviceIdGenerator,
    private val uuidGenerator: UUIDGenerator,
    private val assetCatalogue: AssetCatalogue,
    private val environmentConfig: EnvironmentConfig
) : PersistentPrefs {

    private var isUnderAutomationTesting = false // Don't persist!

    override val isUnderTest: Boolean
        get() = isUnderAutomationTesting

    override fun setIsUnderTest() {
        isUnderAutomationTesting = true
    }

    override val deviceId: String
        get() {
            return if (qaRandomiseDeviceId) {
                uuidGenerator.generateUUID()
            } else {
                var deviceId = getValue(KEY_PRE_IDV_DEVICE_ID, "")
                if (deviceId.isEmpty()) {
                    deviceId = idGenerator.generateId()
                    setValue(KEY_PRE_IDV_DEVICE_ID, deviceId)
                }
                deviceId
            }
        }

    override var pinId: String
        get() = getValue(KEY_PIN_IDENTIFIER) ?: backupStore.getString(KEY_PIN_IDENTIFIER, null) ?: ""
        @SuppressLint("ApplySharedPref")
        set(value) {
            setValue(KEY_PIN_IDENTIFIER, value)
            backupStore.edit().putString(KEY_PIN_IDENTIFIER, value).commit()
            BackupManager.dataChanged(ctx.packageName)
        }

    override var newSwapEnabled: Boolean
        get() = getValue(NEW_SWAP_ENABLED, false)
        set(value) {
            setValue(NEW_SWAP_ENABLED, value)
        }

    override var devicePreIDVCheckFailed: Boolean
        get() = getValue(KEY_PRE_IDV_FAILED, false)
        set(value) = setValue(KEY_PRE_IDV_FAILED, value)

    override var isOnboardingComplete: Boolean
        get() = getValue(KEY_ONBOARDING_COMPLETE, false)
        set(completed) = setValue(KEY_ONBOARDING_COMPLETE, completed)

    override var isCustodialIntroSeen: Boolean
        get() = getValue(KEY_CUSTODIAL_INTRO_SEEN, false)
        set(seen) = setValue(KEY_CUSTODIAL_INTRO_SEEN, seen)

    override var isPrivateKeyIntroSeen: Boolean
        get() = getValue(KEY_PRIVATE_KEY_INTRO_SEEN, false)
        set(seen) = setValue(KEY_PRIVATE_KEY_INTRO_SEEN, seen)

    override var isRewardsIntroSeen: Boolean
        get() = getValue(KEY_REWARDS_INTRO_SEEN, false)
        set(seen) = setValue(KEY_REWARDS_INTRO_SEEN, seen)

    override var remainingSendsWithoutBackup: Int
        get() = getValue(KEY_REMAINING_SENDS_WITHOUT_BACKUP, MAX_ALLOWED_SENDS)
        set(remaining) = setValue(KEY_REMAINING_SENDS_WITHOUT_BACKUP, remaining)

    override var dashboardAssetOrder: List<String>
        get() = getValue(KEY_DASHBOARD_ORDER)?.let {
            try {
                Json.decodeFromString<List<String>>(it)
            } catch (t: Throwable) {
                emptyList()
            }
        } ?: emptyList()
        set(value) {
            setValue(KEY_DASHBOARD_ORDER, Json.encodeToString(value))
        }

    override var hasTappedFabButton: Boolean
        get() = getValue(KEY_TAPPED_FAB, false)
        set(seen) = setValue(KEY_TAPPED_FAB, seen)

    override var qaRandomiseDeviceId: Boolean
        get() = getValue(KEY_IS_DEVICE_ID_RANDOMISED, false)
        set(value) = setValue(KEY_IS_DEVICE_ID_RANDOMISED, value)

    // SecurityPrefs
    override var disableRootedWarning: Boolean
        get() = getValue(PersistentPrefs.KEY_ROOT_WARNING_DISABLED, false)
        set(v) = setValue(PersistentPrefs.KEY_ROOT_WARNING_DISABLED, v)

    override var trustScreenOverlay: Boolean
        get() = getValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, environmentConfig.isRunningInDebugMode())
        set(v) = setValue(PersistentPrefs.KEY_OVERLAY_TRUSTED, v)

    override val areScreenshotsEnabled: Boolean
        get() = getValue(KEY_SCREENSHOTS_ENABLED, false)

    override fun setScreenshotsEnabled(enable: Boolean) =
        setValue(KEY_SCREENSHOTS_ENABLED, enable)

    // From CurrencyPrefs
    override var selectedFiatCurrency: FiatCurrency
        get() = assetCatalogue.fiatFromNetworkTicker(getValue(KEY_SELECTED_FIAT, ""))
            ?: FiatCurrency.locale().takeIf { assetCatalogue.fiatFromNetworkTicker(it.networkTicker) != null }
            ?: FiatCurrency.Dollars
        set(fiat) {
            setValue(KEY_SELECTED_FIAT, fiat.networkTicker)
            tradingCurrency = fiat
        }

    override val noCurrencySet: Boolean
        get() = getValue(KEY_SELECTED_FIAT, "").isEmpty()

    override fun simpleBuyState(): String? {
        return getValue(KEY_SIMPLE_BUY_STATE, "").takeIf { it != "" }
    }

    override fun cardState(): String? {
        return getValue(KEY_CARD_STATE, "").takeIf { it != "" }
    }

    override fun updateCardState(cardState: String) {
        setValue(KEY_CARD_STATE, cardState)
    }

    override fun clearCardState() {
        removeValue(KEY_CARD_STATE)
    }

    override fun updateSupportedCards(cardTypes: String) {
        setValue(KEY_SUPPORTED_CARDS_STATE, cardTypes)
    }

    override fun getSupportedCardTypes(): String? =
        getValue(KEY_SUPPORTED_CARDS_STATE, "").takeIf { it != "" }

    override fun updateSimpleBuyState(simpleBuyState: String) = setValue(KEY_SIMPLE_BUY_STATE, simpleBuyState)

    override fun getBankLinkingState(): String = getValue(KEY_BANK_LINKING, "")

    override fun setBankLinkingState(state: String) = setValue(KEY_BANK_LINKING, state)

    override fun getDynamicOneTimeTokenUrl(): String = getValue(KEY_ONE_TIME_TOKEN_PATH, "")

    override val isRemoteConfigStale: Boolean
        get() = getValue(CONFIG_STALE, false)

    override fun updateRemoteConfigStaleStatus(isStale: Boolean) {
        setValue(CONFIG_STALE, isStale)
    }

    override var installationVersionName: String
        get() = getValue(APP_INSTALLATION_VERSION_NAME, AppInfoPrefs.DEFAULT_APP_VERSION_NAME)
        set(value) {
            setValue(APP_INSTALLATION_VERSION_NAME, value)
        }

    override var currentStoredVersionCode: Int
        get() = getValue(APP_CURRENT_VERSION_CODE, AppInfoPrefs.DEFAULT_APP_VERSION_CODE)
        set(value) {
            setValue(APP_CURRENT_VERSION_CODE, value)
        }

    override fun setDynamicOneTimeTokenUrl(path: String) {
        val previousValue = getDynamicOneTimeTokenUrl()
        if (path.isNotEmpty() && previousValue != path) {
            setValue(KEY_ONE_TIME_TOKEN_PATH, path)
        }
    }

    override fun clearBuyState() = removeValue(KEY_SIMPLE_BUY_STATE)

    override var addCardInfoDismissed: Boolean
        get() = getValue(KEY_ADD_CARD_INFO, false)
        set(dismissed) = setValue(KEY_ADD_CARD_INFO, dismissed)

    override var isFirstTimeBuyer: Boolean
        get() = getValue(KEY_FIRST_TIME_BUYER, true)
        set(value) {
            setValue(KEY_FIRST_TIME_BUYER, value)
        }

    override var tradingCurrency: FiatCurrency
        get() = assetCatalogue.fromNetworkTicker(getValue(KEY_SIMPLE_BUY_CURRENCY, "")) as? FiatCurrency
            ?: selectedFiatCurrency
        set(value) {
            setValue(KEY_SIMPLE_BUY_CURRENCY, value.networkTicker)
        }

    override var hasCompletedAtLeastOneBuy: Boolean
        get() = getValue(KEY_HAS_COMPLETED_AT_LEAST_ONE_BUY, false)
        set(value) {
            setValue(KEY_HAS_COMPLETED_AT_LEAST_ONE_BUY, value)
        }

    override var hasSeenRatingDialog: Boolean
        get() = getValue(HAS_SEEN_RATING, false)
        set(value) = setValue(HAS_SEEN_RATING, value)

    override var preRatingActionCompletedTimes: Int
        get() = getValue(PRE_RATING_ACTION_COMPLETED_TIMES, 0)
        set(value) = setValue(PRE_RATING_ACTION_COMPLETED_TIMES, value)

    // Wallet Status
    override var lastBackupTime: Long
        get() = getValue(BACKUP_DATE_KEY, 0L)
        set(v) = setValue(BACKUP_DATE_KEY, v)

    override val isWalletBackedUp: Boolean
        get() = lastBackupTime != 0L

    override val isWalletFunded: Boolean
        get() = getValue(WALLET_FUNDED_KEY, false)

    override fun setWalletFunded() = setValue(WALLET_FUNDED_KEY, true)

    override val hasMadeBitPayTransaction: Boolean
        get() = getValue(BITPAY_TRANSACTION_SUCCEEDED, false)

    override fun setBitPaySuccess() = setValue(BITPAY_TRANSACTION_SUCCEEDED, true)

    override fun setFeeTypeForAsset(asset: AssetInfo, type: Int) =
        setValue(NETWORK_FEE_PRIORITY_KEY + asset.networkTicker, type)

    override fun getFeeTypeForAsset(asset: AssetInfo): Int =
        getValue(NETWORK_FEE_PRIORITY_KEY + asset.networkTicker, -1)

    override val hasSeenSwapPromo: Boolean
        get() = getValue(SWAP_KYC_PROMO, false)

    override fun setSeenSwapPromo() = setValue(SWAP_KYC_PROMO, true)

    override val hasSeenTradingSwapPromo: Boolean
        get() = getValue(SWAP_TRADING_PROMO, false)

    override fun setSeenTradingSwapPromo() = setValue(SWAP_TRADING_PROMO, true)

    override var isNewlyCreated: Boolean
        get() = getValue(KEY_NEWLY_CREATED_WALLET, false)
        set(newlyCreated) = setValue(KEY_NEWLY_CREATED_WALLET, newlyCreated)

    override var isRestored: Boolean
        get() = getValue(KEY_RESTORED_WALLET, false)
        set(isRestored) = setValue(KEY_RESTORED_WALLET, isRestored)

    override var isAppUnlocked: Boolean
        get() = getValue(KEY_LOGGED_IN, false)
        set(loggedIn) = setValue(KEY_LOGGED_IN, loggedIn)

    override val resendSmsRetries: Int
        get() = getValue(TWO_FA_SMS_RETRIES, MAX_ALLOWED_RETRIES)

    override fun setResendSmsRetries(retries: Int) {
        setValue(TWO_FA_SMS_RETRIES, retries)
    }

    override var email: String
        get() = getValue(KEY_EMAIL, "")
        set(value) = setValue(KEY_EMAIL, value)

    override var countrySelectedOnSignUp: String
        get() = getValue(COUNTRY_SIGN_UP, "")
        set(value) = setValue(COUNTRY_SIGN_UP, value)

    override var stateSelectedOnSignUp: String
        get() = getValue(STATE_SIGNED_UP, "")
        set(value) = setValue(STATE_SIGNED_UP, value)

    override fun clearGeolocationPreferences() {
        removeValue(COUNTRY_SIGN_UP)
        removeValue(STATE_SIGNED_UP)
    }

    // Notification prefs
    override var arePushNotificationsEnabled: Boolean
        get() = getValue(KEY_PUSH_NOTIFICATION_ENABLED, true)
        set(v) = setValue(KEY_PUSH_NOTIFICATION_ENABLED, v)

    override var firebaseToken: String
        get() = getValue(KEY_FIREBASE_TOKEN, "")
        set(v) = setValue(KEY_FIREBASE_TOKEN, v)

    @SuppressLint("ApplySharedPref")
    override fun backupCurrentPrefs(encryptionKey: String, aes: AESUtilWrapper) {
        backupStore.edit()
            .clear()
            .putString(KEY_PIN_IDENTIFIER, getValue(KEY_PIN_IDENTIFIER, ""))
            .putString(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, getValue(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, ""))
            .putString(
                KEY_ENCRYPTED_GUID,
                aes.encrypt(
                    getValue(KEY_WALLET_GUID, ""),
                    encryptionKey,
                    AESUtil.PIN_PBKDF2_ITERATIONS_GUID
                )
            )
            .putString(
                KEY_ENCRYPTED_SHARED_KEY,
                aes.encrypt(
                    getValue(KEY_SHARED_KEY, ""),
                    encryptionKey,
                    AESUtil.PIN_PBKDF2_ITERATIONS_SHAREDKEY
                )
            )
            .commit()

        BackupManager.dataChanged(ctx.packageName)
    }

    override fun restoreFromBackup(decryptionKey: String, aes: AESUtilWrapper) {
        // Pull in the values from the backup, we don't have local state
        setValue(
            KEY_PIN_IDENTIFIER,
            backupStore.getString(KEY_PIN_IDENTIFIER, "") ?: ""
        )
        setValue(
            PersistentPrefs.KEY_ENCRYPTED_PASSWORD,
            backupStore.getString(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, "") ?: ""
        )
        setValue(
            KEY_WALLET_GUID,
            aes.decrypt(
                backupStore.getString(KEY_ENCRYPTED_GUID, ""),
                decryptionKey,
                AESUtil.PIN_PBKDF2_ITERATIONS_GUID
            )
        )
        setValue(
            KEY_SHARED_KEY,
            aes.decrypt(
                backupStore.getString(KEY_ENCRYPTED_SHARED_KEY, ""),
                decryptionKey,
                AESUtil.PIN_PBKDF2_ITERATIONS_SHAREDKEY
            )
        )
    }

    override var backupEnabled: Boolean
        get() = getValue(KEY_CLOUD_BACKUP_ENABLED, true)
        set(value) {
            setValue(KEY_CLOUD_BACKUP_ENABLED, value)
            if (!value) {
                clearBackup()
            }
        }

    override fun hasBackup(): Boolean =
        backupEnabled && backupStore.getString(KEY_ENCRYPTED_GUID, "").isNullOrEmpty().not()

    @SuppressLint("ApplySharedPref")
    override fun clearBackup() {
        // We need to set all the backed values here and not just clear(), since that deletes the
        // prefs files, so there is nothing to back up, so the next restore will return the wallet
        // we just logged out of.
        backupStore.edit()
            .putString(KEY_PIN_IDENTIFIER, "")
            .putString(PersistentPrefs.KEY_ENCRYPTED_PASSWORD, "")
            .putString(KEY_ENCRYPTED_GUID, "")
            .putString(KEY_ENCRYPTED_SHARED_KEY, "")
            .commit()

        BackupManager.dataChanged(ctx.packageName)
    }

    override var encodedPin: String
        get() = decodeFromBase64ToString(getValue(KEY_ENCRYPTED_PIN_CODE, ""))
        set(value) = setValue(KEY_ENCRYPTED_PIN_CODE, encodeToBase64(value))

    override var biometricsEnabled: Boolean
        get() = getValue(KEY_FINGERPRINT_ENABLED, false)
        set(value) = setValue(KEY_FINGERPRINT_ENABLED, value)

    override var sharedKey: String
        get() = getValue(KEY_SHARED_KEY, "")
        set(value) = setValue(KEY_SHARED_KEY, value)

    override var walletGuid: String
        get() = getValue(KEY_WALLET_GUID, "")
        set(value) = setValue(KEY_WALLET_GUID, value)

    override var encryptedPassword: String
        get() = getValue(KEY_ENCRYPTED_PASSWORD, "")
        set(value) = setValue(KEY_ENCRYPTED_PASSWORD, value)

    override var pinFails: Int
        get() = getValue(KEY_PIN_FAILS, 0)
        set(value) = setValue(KEY_PIN_FAILS, value)

    override fun clearEncodedPin() {
        removeValue(KEY_ENCRYPTED_PIN_CODE)
    }

    override var sessionId: String
        get() = getValue(SESSION_ID, "")
        set(value) = setValue(SESSION_ID, value)

    override var emailVerified: Boolean
        get() = getValue(KEY_EMAIL_VERIFIED, false)
        set(value) = setValue(KEY_EMAIL_VERIFIED, value)

    // Onboarding Prefs
    override var isLandingCtaDismissed: Boolean
        get() = getValue(KEY_IS_LANDING_CTA_DISMISSED, false)
        set(value) = setValue(KEY_IS_LANDING_CTA_DISMISSED, value)

    override var isEntitySwitchSilverKycUpsellDismissed: Boolean
        get() = getValue(KEY_IS_ENTITY_SWITCH_SILVER_KYC_UPSELL_DISMISSED, false)
        set(value) = setValue(KEY_IS_ENTITY_SWITCH_SILVER_KYC_UPSELL_DISMISSED, value)

    override var isSendNetworkWarningDismissed: Boolean
        get() = getValue(KEY_IS_SEND_NETWORK_WARNING_DISMISSED, false)
        set(value) = setValue(KEY_IS_SEND_NETWORK_WARNING_DISMISSED, value)

    override fun clearSessionId() = removeValue(SESSION_ID)

    override fun removePinID() {
        removeValue(KEY_PIN_FAILS)
    }

    private fun encodeToBase64(data: String) =
        Base64.encodeToString(data.toByteArray(charset("UTF-8")), Base64.DEFAULT)

    private fun decodeFromBase64ToString(data: String): String =
        String(Base64.decode(data.toByteArray(charset("UTF-8")), Base64.DEFAULT))

    // Raw accessors
    override fun getValue(name: String): String? =
        store.getString(name, null)

    override fun getValue(name: String, defaultValue: String): String =
        store.getString(name, defaultValue).orEmpty()

    override fun getValue(name: String, defaultValue: Int): Int =
        store.getInt(name, defaultValue)

    override fun getValue(name: String, defaultValue: Long): Long =
        try {
            store.getLong(name, defaultValue)
        } catch (e: Exception) {
            store.getInt(name, defaultValue.toInt()).toLong()
        }

    override fun getValue(name: String, defaultValue: Boolean): Boolean =
        store.getBoolean(name, defaultValue)

    override fun setValue(name: String, value: String) {
        store.edit().putString(name, value).apply()
    }

    override fun setValue(name: String, value: Int) {
        store.edit().putInt(name, if (value < 0) 0 else value).apply()
    }

    override fun setValue(name: String, value: Long) {
        store.edit().putLong(name, if (value < 0L) 0L else value).apply()
    }

    override fun setValue(name: String, value: Boolean) {
        store.edit().putBoolean(name, value).apply()
    }

    override fun has(name: String): Boolean = store.contains(name)

    override fun removeValue(name: String) {
        store.edit().remove(name).apply()
    }

    override fun clear() {
        val versionCode = store.getInt(APP_CURRENT_VERSION_CODE, AppInfoPrefs.DEFAULT_APP_VERSION_CODE)
        val installedVersion = store.getString(APP_INSTALLATION_VERSION_NAME, AppInfoPrefs.DEFAULT_APP_VERSION_NAME)
            ?: AppInfoPrefs.DEFAULT_APP_VERSION_NAME
        val firebaseToken = store.getString(KEY_FIREBASE_TOKEN, "").orEmpty()
        val isLandingCtaDismissed = store.getBoolean(KEY_IS_LANDING_CTA_DISMISSED, false)

        store.edit().clear().apply()

        setValue(APP_CURRENT_VERSION_CODE, versionCode)
        setValue(APP_INSTALLATION_VERSION_NAME, installedVersion)
        setValue(KEY_FIREBASE_TOKEN, firebaseToken)
        setValue(KEY_IS_LANDING_CTA_DISMISSED, isLandingCtaDismissed)

        clearBackup()
    }

    // Secure Channel Prefs
    override val deviceKey: String
        get() = if (has(KEY_SECURE_CHANNEL_IDENTITY_KEY)) {
            getValue(KEY_SECURE_CHANNEL_IDENTITY_KEY, "")
        } else {
            val key = ECKey()
            setValue(KEY_SECURE_CHANNEL_IDENTITY_KEY, key.privateKeyAsHex)
            getValue(KEY_SECURE_CHANNEL_IDENTITY_KEY, "")
        }

    private fun getBrowserIdentityMapping() =
        Json.decodeFromString<BrowserIdentityMapping>(
            getValue(KEY_SECURE_CHANNEL_BROWSER_MAPPINGS, """{ "mapping": {} }""")
        )

    private fun setBrowserIdentityMapping(browserIdentity: BrowserIdentityMapping) =
        setValue(KEY_SECURE_CHANNEL_BROWSER_MAPPINGS, Json.encodeToString(browserIdentity))

    override fun getBrowserIdentity(pubkeyHash: String): BrowserIdentity? {
        val browserIdentityMapping = getBrowserIdentityMapping()
        val mapping = browserIdentityMapping.mapping
        return mapping.get(pubkeyHash)
    }

    override fun addBrowserIdentity(browserIdentity: BrowserIdentity) {
        getBrowserIdentityMapping().mapping
            .also { it.put(browserIdentity.pubKeyHash(), browserIdentity) }
            .also { setBrowserIdentityMapping(BrowserIdentityMapping(it)) }
    }

    override fun removeBrowserIdentity(pubkeyHash: String) {
        getBrowserIdentityMapping().mapping
            .also { it.remove(pubkeyHash) }
            .also { setBrowserIdentityMapping(BrowserIdentityMapping(it)) }
    }

    override fun updateBrowserIdentityUsedTimestamp(pubkeyHash: String) {
        getBrowserIdentityMapping().mapping
            .also { it.get(pubkeyHash)?.lastUsed = System.currentTimeMillis() }
            .also { setBrowserIdentityMapping(BrowserIdentityMapping(it)) }
    }

    override fun addBrowserIdentityAuthorization(pubkeyHash: String, authorization: Authorization) {
        getBrowserIdentityMapping().mapping
            .also { it.get(pubkeyHash)?.authorized?.add(authorization) }
            .also { setBrowserIdentityMapping(BrowserIdentityMapping(it)) }
    }

    override fun pruneBrowserIdentities() {
        getBrowserIdentityMapping().mapping
            .filterNot {
                it.value.lastUsed == 0L &&
                    (System.currentTimeMillis() - it.value.scanned) > TimeUnit.MINUTES.toMillis(2)
            }
            .toMutableMap()
            .also { setBrowserIdentityMapping(BrowserIdentityMapping(it)) }
    }

    /**
     * Clears everything but the GUID for logging back in and the deviceId - for pre-IDV checking
     */
    override fun unPairWallet() {
        val guid = getValue(KEY_WALLET_GUID, "")
        val deviceId = getValue(KEY_PRE_IDV_DEVICE_ID, "")

        clear()

        setValue(KEY_LOGGED_IN, true)
        setValue(KEY_WALLET_GUID, guid)
        setValue(KEY_PRE_IDV_DEVICE_ID, deviceId)
    }

    companion object {
        const val KEY_PRE_IDV_FAILED = "pre_idv_check_failed"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_PIN_IDENTIFIER = "pin_kookup_key" // Historical misspelling. DO NOT FIX.

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_SELECTED_FIAT = "ccurrency" // Historical misspelling, don't update

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_PRE_IDV_DEVICE_ID = "pre_idv_device_id"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_LOGGED_IN = "logged_in"

        private const val KEY_SIMPLE_BUY_STATE = "key_simple_buy_state_2"
        private const val KEY_CARD_STATE = "key_card_state"
        private const val KEY_ADD_CARD_INFO = "key_add_card_info"
        private const val KEY_FIRST_TIME_BUYER = "key_first_time_buyer"
        private const val KEY_SIMPLE_BUY_CURRENCY = "key_trading_urrency_currency"
        private const val KEY_HAS_COMPLETED_AT_LEAST_ONE_BUY = "has_completed_at_least_one_buy"

        private const val KEY_SUPPORTED_CARDS_STATE = "key_supported_cards"
        private const val KEY_BANK_LINKING = "KEY_BANK_LINKING"
        private const val KEY_ONE_TIME_TOKEN_PATH = "KEY_ONE_TIME_TOKEN_PATH"

        private const val KEY_CUSTODIAL_INTRO_SEEN = "key_custodial_balance_intro_seen"
        private const val KEY_PRIVATE_KEY_INTRO_SEEN = "key_private_key_intro_seen"
        private const val KEY_REWARDS_INTRO_SEEN = "key_rewards_intro_seen"
        private const val KEY_REMAINING_SENDS_WITHOUT_BACKUP = "key_remaining_sends_without_backup"
        private const val MAX_ALLOWED_SENDS = 5
        private const val KEY_TAPPED_FAB = "key_tapped_fab"
        private const val KEY_SCREENSHOTS_ENABLED = "screenshots_enabled"
        private const val KEY_ONBOARDING_COMPLETE = "KEY_ONBOARDING_COMPLETE"

        private const val BACKUP_DATE_KEY = "BACKUP_DATE_KEY"
        private const val WALLET_FUNDED_KEY = "WALLET_FUNDED_KEY"
        private const val BITPAY_TRANSACTION_SUCCEEDED = "BITPAY_TRANSACTION_SUCCEEDED"
        private const val NETWORK_FEE_PRIORITY_KEY = "fee_type_key_"
        private const val SWAP_KYC_PROMO = "SWAP_KYC_PROMO"
        private const val SWAP_TRADING_PROMO = "SWAP_TRADING_PROMO"
        private const val KEY_NEWLY_CREATED_WALLET = "newly_created_wallet"
        private const val KEY_RESTORED_WALLET = "restored_wallet"

        private const val TWO_FA_SMS_RETRIES = "TWO_FA_SMS_RETRIES"
        private const val KEY_EMAIL = "KEY_EMAIL"
        private const val COUNTRY_SIGN_UP = "COUNTRY_SIGN_UP"
        private const val STATE_SIGNED_UP = "STATE_SIGNED_UP"
        private const val APP_CURRENT_VERSION_CODE = "APP_CURRENT_VERSION_CODE"
        private const val APP_INSTALLATION_VERSION_NAME = "APP_INSTALLATION_VERSION_NAME"
        const val MAX_ALLOWED_RETRIES = 3

        // For QA:
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_IS_DEVICE_ID_RANDOMISED = "random_device_id"

        const val NEW_SWAP_ENABLED = "swap_v_2_enabled"

        private const val KEY_FIREBASE_TOKEN = "firebase_token"
        private const val KEY_PUSH_NOTIFICATION_ENABLED = "push_notification_enabled"

        // Cloud backup keys
        private const val KEY_ENCRYPTED_GUID = "encrypted_guid"
        private const val KEY_ENCRYPTED_SHARED_KEY = "encrypted_shared_key"
        private const val KEY_CLOUD_BACKUP_ENABLED = "backup_enabled"
        private const val KEY_SECURE_CHANNEL_IDENTITY_KEY = "secure_channel_identity"
        private const val KEY_SECURE_CHANNEL_BROWSER_MAPPINGS = "secure_channel_browsers"

        // Rating
        private const val HAS_SEEN_RATING = "has_seen_rating"
        private const val PRE_RATING_ACTION_COMPLETED_TIMES = "pre_rating_action_completed_times"

        // Onboarding
        private const val KEY_IS_LANDING_CTA_DISMISSED = "KEY_IS_LANDING_PAGE_DISMISSED"
        private const val KEY_IS_ENTITY_SWITCH_SILVER_KYC_UPSELL_DISMISSED =
            "KEY_IS_ENTITY_SWITCH_SILVER_KYC_UPSELL_DISMISSED"
        private const val KEY_IS_SEND_NETWORK_WARNING_DISMISSED =
            "KEY_IS_SEND_NETWORK_WARNING_DISMISSED"

        // Auth prefs
        // NOTE: for historical purposes, should be used as the cryptography cipher key
        private const val KEY_ENCRYPTED_PIN_CODE = "encrypted_pin_code"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"

        private const val KEY_WALLET_GUID = "guid"
        private const val KEY_SHARED_KEY = "sharedKey"
        private const val KEY_ENCRYPTED_PASSWORD = "encrypted_password"
        private const val KEY_PIN_FAILS = "pin_fails"
        const val SESSION_ID = "session_id"

        private const val CONFIG_STALE = "CONFIG_STALE"

        private const val KEY_DASHBOARD_ORDER = "dashboard_asset_order"

        // AppUpdate (app maintenance)
        private const val KEY_SKIPPED_VERSION_CODE = "KEY_SKIPPED_VERSION_CODE"
    }
}

fun BrowserIdentity.pubKeyHash() = Sha256Hash.of(Hex.decode(this.pubkey)).toString()
