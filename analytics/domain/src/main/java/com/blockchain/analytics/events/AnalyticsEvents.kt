package com.blockchain.analytics.events

import com.blockchain.analytics.AnalyticsEvent

@Deprecated("Analytics events should be defined near point of use")
enum class AnalyticsEvents(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    AccountsAndAddresses("accounts_and_addresses"),
    Backup("backup"),
    Dashboard("dashboard"),
    Exchange("exchange"),
    ExchangeCreate("exchange_create"),
    ExchangeDetailConfirm("exchange_detail_confirm"),
    ExchangeDetailOverview("exchange_detail_overview"),
    ExchangeExecutionError("exchange_execution_error"),
    ExchangeHistory("exchange_history"),
    KycEmail("kyc_email"),
    KycAddress("kyc_address"),
    KycComplete("kyc_complete"),
    SwapTiers("swap_tiers"),
    KycTiersLocked("kyc_tiers_locked"),
    KycTier1Complete("kyc_tier1_complete"),
    KycTier2Complete("kyc_tier2_complete"),
    KycCountry("kyc_country"),
    KycProfile("kyc_profile"),
    KycStates("kyc_states"),
    KycVerifyIdentity("kyc_verify_identity"),
    KycWelcome("kyc_welcome"),
    KycResubmission("kyc_resubmission"),
    KycSunriverStart("kyc_sunriver_start"),
    KycBlockstackStart("kyc_blockstack_start"),
    KycSimpleBuyStart("kyc_simple_buy_start"),
    KycFiatFundsStart("kyc_fiat_funds_start"),
    KycInterestStart("kyc_interest_start"),
    KycMoreInfo("kyc_more_info"),
    KycTiers("kyc_tiers"),
    Logout("logout"),
    Settings("settings"),
    Support("support"),
    WebLogin("web_login"),
    SwapErrorDialog("swap_error_dialog"),
    WalletCreation("wallet_creation"),
    WalletManualLogin("wallet_manual_login"),
    PITDEEPLINK("pit_deeplink"),
    WalletAutoPairing("wallet_auto_pairing"),
    ChangeFiatCurrency("currency"),
    OpenAssetsSelector("asset_selector_open"),
    CloseAssetsSelector("asset_selector_open"),
    CameraSystemPermissionApproved("permission_sys_camera_approve"),
    CameraSystemPermissionDeclined("permission_sys_camera_decline"),

    WalletSignupOpen("wallet_signup_open"),
    WalletSignupClickCreate("wallet_signup_create"),
    WalletSignupClickEmail("wallet_signup_email"),
    WalletSignupClickPasswordFirst("wallet_signup_password_first"),
    WalletSignupClickPasswordSecond("wallet_signup_password_second"),
    WalletSignupCreated("wallet_signup_wallet_created"),
    WalletSignupPINFirst("wallet_signup_pin_first"),
    WalletSignupPINSecond("wallet_signup_pin_second"),
    WalletSignupFirstLogIn("wallet_signup_login")
}

fun kycTierStart(tier: Int): AnalyticsEvent = object : AnalyticsEvent {
    override val event: String = "kyc_tier${tier}_start"
    override val params: Map<String, String> = emptyMap()
}

fun networkError(host: String, path: String, message: String): AnalyticsEvent = object : AnalyticsEvent {
    override val event: String
        get() = "network_error"
    override val params: Map<String, String>
        get() = mapOf("host" to host, "message" to message, "path" to path)
}

fun apiError(host: String, path: String, body: String?, requestId: String?, errorCode: Int): AnalyticsEvent =
    object : AnalyticsEvent {
        override val event: String
            get() = "api_error"
        override val params: Map<String, String>
            get() = mapOf(
                "host" to host,
                "body" to body,
                "path" to path,
                "error_code" to errorCode.toString(),
                "request_id" to requestId
            ).mapNotNull { it.value?.let { value -> it.key to value } }.toMap()
    }

enum class AnalyticsNames(val eventName: String) {
    // App Events
    APP_INSTALLED("Application Installed"),
    APP_UPDATED("Application Updated"),
    APP_DEEP_LINK_OPENED("Deep Link Opened"),
    APP_BACKGROUNDED("Application Backgrounded"),
    BUY_AMOUNT_ENTERED("Buy Amount Entered"),
    BUY_FREQUENCY_SELECTED("Buy Frequency Selected"),
    BUY_PAYMENT_METHOD_CHANGED("Buy Payment Method Selected"),
    BUY_SELL_CLICKED("Buy Sell Clicked"),
    BUY_SELL_VIEWED("Buy Sell Viewed"),
    BUY_METHOD_OPTION_VIEWED("Buy Method Option Viewed"),
    DEPOSIT_METHOD_OPTION_VIEWED("Deposit Method Option Viewed"),
    WITHDRAWAL_METHOD_OPTION_VIEWED("Withdrawal Method Option Viewed"),
    SIGNED_IN("Signed In"),
    SIGNED_OUT("Signed Out"),
    SWAP_VIEWED("Swap Viewed"),
    SWAP_CLICKED("Swap Clicked"),
    SWAP_RECEIVE_SELECTED("Swap Receive Selected"),
    SWAP_MAX_CLICKED("Swap Amount Max Clicked"),
    SWAP_FROM_SELECTED("Swap From Selected"),
    SWAP_ACCOUNTS_SELECTED("Swap Accounts Selected"),
    SWAP_AMOUNT_ENTERED("Swap Amount Entered"),
    AMOUNT_SWITCHED("Amount Switched"),
    SWAP_REQUESTED("Swap Requested"),
    SEND_MAX_CLICKED("Send Amount Max Clicked"),
    SEND_RECEIVE_CLICKED("Send Receive Clicked"),
    SEND_FROM_SELECTED("Send From Selected"),
    SEND_SUBMITTED("Send Submitted"),
    SEND_RECEIVE_VIEWED("Send Receive Viewed"),
    RECEIVE_ACCOUNT_SELECTED("Receive Currency Selected"),
    RECEIVE_ADDRESS_COPIED("Receive Details Copied"),
    WITHDRAWAL_AMOUNT_ENTERED("Withdrawal Amount Entered"),
    WITHDRAWAL_MAX_CLICKED("Withdrawal Amount Max Clicked"),
    WITHDRAWAL_CLICKED("Withdrawal Clicked"),
    WITHDRAWAL_METHOD_SELECTED("Withdrawal Method Selected"),
    LINK_BANK_CONDITIONS_APPROVED("Link Bank Conditions Approved"),
    LINK_BANK_CLICKED("Link Bank Clicked"),
    BANK_SELECTED("Link Bank Selected"),
    SELL_AMOUNT_ENTERED("Sell Amount Entered"),
    SELL_SOURCE_SELECTED("Sell From Selected"),
    SELL_AMOUNT_MAX_CLICKED("Sell Amount Max Clicked"),
    DEPOSIT_CLICKED("Deposit Clicked"),
    DEPOSIT_AMOUNT_ENTERED("Deposit Amount Entered"),
    DEPOSIT_METHOD_SELECTED("Deposit Method Selected"),
    BANK_TRANSFER_VIEWED("Bank Transfer Viewed"),
    BANK_TRANSFER_CLICKED("Bank Transfer Clicked"),
    INTEREST_CLICKED("Interest Clicked"),
    INTEREST_DEPOSIT_AMOUNT_ENTERED("Interest Deposit Amount Entered"),
    INTEREST_DEPOSIT_CLICKED("Interest Deposit Clicked"),
    INTEREST_MAX_CLICKED("Interest Deposit Max Amount Clicked"),
    INTEREST_DEPOSIT_VIEWED("Interest Deposit Viewed"),
    INTEREST_VIEWED("Interest Viewed"),
    INTEREST_WITHDRAWAL_CLICKED("Interest Withdrawal Clicked"),
    INTEREST_WITHDRAWAL_VIEWED("Interest Withdrawal Viewed"),
    ACCOUNT_PASSWORD_CHANGED("Account Password Changed"),
    CHANGE_PIN_CODE_CLICKED("Change Pin Clicked"),
    CHANGE_EMAIL_CLICKED("Email Change Clicked"),
    BIOMETRICS_OPTION_UPDATED("Biometrics Updated"),
    PIN_CODE_CHANGED("Mobile Pin Code Changed"),
    RECOVERY_PHRASE_SHOWN("Recovery Phrase Shown"),
    TWO_STEP_VERIFICATION_CODE_CLICKED("Two Step Verification Option Clicked"),
    TWO_STEP_VERIFICATION_CODE_SUBMITTED("Verification Code Submitted"),
    UPGRADE_KYC_VERIFICATION_CLICKED("Upgrade Verification Clicked"),
    EMAIL_VERIF_SKIPPED("Email Verification Skipped"),
    REMOVE_CARD_CLICKED("Remove Linked Card Clicked"),
    SETTINGS_HYPERLINK_DESTINATION("Settings Hyperlink Clicked"),
    NOTIFICATION_PREFS_UPDATED("Notification Preferences Updated"),
    LINK_CARD_CLICKED("Link Card Clicked"),
    CHANGE_MOBILE_NUMBER_CLICKED("Change Mobile Number Clicked"),
    EMAIL_VERIFF_REQUESTED("Email Verification Requested"),
    RECURRING_BUY_CANCEL_CLICKED("Cancel Recurring Buy Clicked"),
    RECURRING_BUY_CLICKED("Recurring Buy Clicked"),
    RECURRING_BUY_INFO_VIEWED("Recurring Buy Info Viewed"),
    RECURRING_BUY_LEARN_MORE_CLICKED("Recurring Buy Learn More Clicked"),
    RECURRING_BUY_DETAILS_CLICKED("Recurring Buy Details Clicked"),
    RECURRING_BUY_SUGGESTION_SKIPPED("Recurring Buy Suggestion Skipped"),
    RECURRING_BUY_VIEWED("Recurring Buy Viewed"),
    RECURRING_BUY_UNAVAILABLE_SHOWN("Recurring Buy Unavailable Shown"),
    WALLET_SIGN_UP("Wallet Signed Up"),
    WALLET_SIGN_UP_COUNTRY_SELECTED("Sign Up Country Selected"),
    WALLET_SIGN_UP_STATE_SELECTED("Sign Up Country State Selected"),
    LOGIN_DEVICE_VERIFIED("Device Verified"),
    LOGIN_CTA_CLICKED("Login Clicked"),
    LOGIN_HELP_CLICKED("Login Help Clicked"),
    LOGIN_ID_ENTERED("Login Identifier Entered"),
    LOGIN_LEARN_MORE_CLICKED("Login Learn More Clicked"),
    LOGIN_METHOD_SELECTED("Login Method Selected"),
    LOGIN_PASSWORD_DENIED("Login Password Denied"),
    LOGIN_PASSWORD_ENTERED("Login Password Entered"),
    LOGIN_REQUEST_APPROVED("Login Request Approved"),
    LOGIN_REQUEST_DENIED("Login Request Denied"),
    LOGIN_2FA_DENIED("Login Two Step Verification Denied"),
    LOGIN_2FA_ENTERED("Login Two Step Verification Entered"),
    LOGIN_VIEWED("Login Viewed"),
    LOGIN_FAILED("Login Request Failed"),
    LOGIN_EMAIL_FAILED("Login Identifier Failed"),
    RECOVERY_PASSWORD_RESET("Account Password Reset"),
    RECOVERY_FAILED("Account Recovery Failed"),
    RECOVERY_CLOUD_BACKUP_SCANNED("Cloud Backup Code Scanned"),
    RECOVERY_NEW_PASSWORD("New Account Password Entered"),
    RECOVERY_OPTION_SELECTED("Recovery Option Selected"),
    RECOVERY_MNEMONIC_ENTERED("Recovery Phrase Entered"),
    RECOVERY_RESET_CANCELLED("Reset Account Cancelled"),
    RECOVERY_RESET_CLICKED("Reset Account Clicked"),
    LANDING_CTA_LOGIN_CLICKED("Login Clicked"),
    LANDING_CTA_SIGNUP_CLICKED("Sign Up Clicked"),
    DASHBOARD_ONBOARDING_VIEWED("Peeksheet Viewed"),
    DASHBOARD_ONBOARDING_DISMISSED("Peeksheet Dismissed"),
    DASHBOARD_ONBOARDING_CARD_CLICKED("Peeksheet Process Clicked"),
    DASHBOARD_ONBOARDING_STEP_LAUNCHED("Peeksheet Selection Clicked"),
    CURRENCY_SELECTION_TRADING_CURRENCY_CHANGED("Fiat Currency Selected"),
    CAMERA_PERMISSION_CHECKED("Camera Permission Checked"),
    CAMERA_PERMISSION_REQUESTED("Camera Permission Requested Actioned"),
    CONNECTED_DAPP_ACTIONED("Connected Dapp Actioned"),
    CONNECTED_DAPP_CLICKED("Connected Dapp Clicked"),
    CONNECTED_DAPPS_LIST_CLICKED("Connected Dapps List Clicked"),
    CONNECTED_DAPPS_LIST_VIEWED("Connected Dapps List Viewed"),
    DAPP_CONNECTION_ACTIONED("Dapp Connection Actioned"),
    DAPP_CONNECTION_CONFIRMED("Dapp Connection Confirmed"),
    DAPP_CONNECTION_REJECTED("Dapp Connection Rejected"),
    DAPP_REQUEST_ACTIONED("Dapp Request Actioned"),
    QR_CODE_CLICKED("Qr Code Clicked"),
    QR_CODE_SCANNED("Qr Code Scanned"),
    TERMS_CONDITIONS_VIEWED("T&C Viewed"),
    VERIFICATION_SUBMISSION_FAILED("Verification Submission Failed"),
    TERMS_CONDITIONS_ACCEPTED("T&C Accepted"),
    COINVIEW_REWARDS_WITHDRAW_ADD_CLICKED("Rewards Withdraw Add Clicked"),
    COINVIEW_WALLETS_ACCOUNTS_VIEWED("Wallets Accounts Viewed"),
    COINVIEW_WALLETS_ACCOUNTS_CLICKED("Wallets Accounts Clicked"),
    COINVIEW_TRANSACTION_CLICKED("Transaction Type Clicked"),
    COINVIEW_SEND_RECEIVE_CLICKED("Send Receive Clicked"),
    COINVIEW_BUY_RECEIVE_CLICKED("Buy Receive Clicked"),
    COINVIEW_CHART_INTERVAL_SELECTED("Chart Time Interval Selected"),
    COINVIEW_CHART_ENGAGED("Chart Engaged"),
    COINVIEW_CHART_DISENGAGED("Chart Disengaged"),
    COINVIEW_PAST_TRANSACTION_CLICKED("Past Transaction Clicked"),
    COINVIEW_HYPERLINK_CLICKED("Hyperlink Clicked"),
    COINVIEW_EXPLAINER_ACCEPTED("Explainer Accepted"),
    COINVIEW_EXPLAINER_VIEWED("Explainer Viewed"),
    COINVIEW_CONNECT_EXCHANGE_ACTIONED("Connect To The Exchange Actioned"),
    COINVIEW_COINVIEW_OPEN("Coin View Open"),
    COINVIEW_COINVIEW_CLOSE("Coin View Closed"),
    COINVIEW_REMOVED_FROM_WATCHLIST("Coin Removed From Watchlist"),
    COINVIEW_ADDED_WATCHLIST("Coin Added To Watchlist"),
    TX_INFO_KYC_UPSELL_CLICKED("Get More Access When You Verify Clicked"),
    TX_INFO_KYC_UPSELL_DISMISSED("Get More Access When You Verify Dismissed"),
    KYC_MORE_INFO_VIEWED("Pre Verification Viewed"),
    KYC_MORE_INFO_CTA_CLICKED("Pre Verification CTA Clicked"),
    KYC_MORE_INFO_DISMISSED("Pre Verification Dismissed"),
    KYC_UPGRADE_NOW_VIEWED("Trading Limits Viewed"),
    KYC_UPGRADE_NOW_GET_BASIC_CLICKED("Trading Limits Get Basic CTA Clicked"),
    KYC_UPGRADE_NOW_GET_VERIFIED_CLICKED("Trading Limits Get Verified CTA Clicked"),
    KYC_UPGRADE_NOW_DISMISSED("Trading Limits Dismissed"),
    ENTITY_SWITCH_SILVER_KYC_UPSELL_VIEWED("Verify Now Pop Up Viewed"),
    ENTITY_SWITCH_SILVER_KYC_UPSELL_CTA_CLICKED("Verify Now Pop Up CTA Clicked"),
    ENTITY_SWITCH_SILVER_KYC_UPSELL_DISMISSED("Verify Now Pop Up Dismissed"),
    KYC_QUESTIONNAIRE_VIEWED("Account Info Screen Viewed"),
    KYC_QUESTIONNAIRE_SUBMITTED("Account Info Submitted"),
    PUSH_NOTIFICATION_RECEIVED("Push Notification Received"),
    PUSH_NOTIFICATION_TAPPED("Push Notification Tapped"),
    PUSH_NOTIFICATION_MISSING_DATA("Push Notification Missing Data"),
    CUSTOMER_SUPPORT_CLICKED("Customer Support Clicked"),
    CUSTOMER_SUPPORT_CONTACT_US_CLICKED("Contact Us Clicked"),
    CUSTOMER_SUPPORT_FAQ_CLICKED("View FAQs Clicked"),
    CLIENT_ERROR("Client Error"),
    NOTIFICATION_CLICKED("Notification Clicked"),
    NOTIFICATION_VIEWED("Notification Viewed"),
    NOTIFICATION_PREFERENCES_CLICKED("Notification Preferences Clicked"),
    NOTIFICATION_PREFERENCES_VIEWED("Notification Preferences Viewed"),
    NOTIFICATIONS_CLOSED("Notifications Closed"),
    NOTIFICATION_NEWS_SET_UP("News Set Up"),
    NOTIFICATION_PRICE_ALERTS_SET_UP("Price Alerts Set Up"),
    NOTIFICATION_SECURITY_ALERTS_SET_UP("Security Alerts Set Up"),
    NOTIFICATION_WALLET_ACTIVITY_SET_UP("Wallet Activity Set Up"),
    NOTIFICATION_STATUS_CHANGE_ERROR("Status Change Error"),
    UI_TOUR_VIEWED("UI Tour Viewed"),
    UI_TOUR_CTA_CLICKED("UI Tour CTA Clicked"),
    UI_TOUR_PROGRESS_CLICKED("UI Tour Progress Clicked"),
    UI_TOUR_DISMISSED("UI Tour Progress Dismissed"),
    WALLET_ACTIVITY_VIEWED("Wallet Activity Viewed"),
    WALLET_BUY_SELL_VIEWED("Wallet Buy Sell Viewed"),
    WALLET_FAB_VIEWED("Wallet FAB Viewed"),
    WALLET_HOME_VIEWED("Wallet Home Viewed"),
    WALLET_PRICES_VIEWED("Wallet Prices Viewed"),
    WALLET_REWARDS_VIEWED("Wallet Rewards Viewed"),
    REFERRAL_CTA_CLICKED("Clicks on referral CTAs"),
    REFERRAL_VIEW_REFERRAL("View referrals page"),
    REFERRAL_SHARE_CODE("Share referral code"),
    REFERRAL_COPY_CODE("Referral code copied"),
    ;
}

enum class LaunchOrigin {
    NAVIGATION,
    SEND,
    SWAP,
    AIRDROP,
    RESUBMISSION,
    SIMPLETRADE,
    DASHBOARD_PROMO,
    DASHBOARD,
    TRANSACTION_LIST,
    TRANSACTION_DETAILS,
    DEPOSIT,
    BUY,
    WITHDRAW,
    CURRENCY_PAGE,
    DEEPLINK,
    NOTIFICATION,
    SAVINGS,
    FIAT_FUNDS,
    SIGN_UP,
    SETTINGS,
    SAVINGS_PAGE,
    VERIFICATION,
    FAB,
    PRICES,
    HOME,
    DCA_DETAILS_LINK,
    BUY_CONFIRMATION,
    RECURRING_BUY_DETAILS,
    RECURRING_BUY,
    APPS_LIST,
    QR_CODE,
    LAUNCH_SCREEN,
    COIN_VIEW,
    NUX_LAUNCH_PROMO_LOG_IN,
    NUX_LAUNCH_PROMO_BUY_CRYPTO;
}
