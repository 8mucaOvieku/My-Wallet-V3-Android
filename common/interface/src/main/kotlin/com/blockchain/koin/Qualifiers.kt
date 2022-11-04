package com.blockchain.koin

import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named

val applicationScope = StringQualifier("applicationScope")
val featureFlagsPrefs = StringQualifier("FeatureFlagsPrefs")
val feynmanEnterAmountFeatureFlag = StringQualifier("ff_enter_amount_feynman")
val feynmanCheckoutFeatureFlag = StringQualifier("ff_checkout_feynman")
val googlePayFeatureFlag = StringQualifier("ff_gpay")
val superAppFeatureFlag = StringQualifier("ff_super_app")
val intercomChatFeatureFlag = StringQualifier("ff_intercom_chat")
val blockchainCardFeatureFlag = StringQualifier("ff_blockchain_card")
val coinNetworksFeatureFlag = StringQualifier("ff_coin_networks")
val ethLayerTwoFeatureFlag = StringQualifier("ff_eth_layer_two")
val evmWithoutL1BalanceFeatureFlag = StringQualifier("ff_evm_native_balance")
val backupPhraseFeatureFlag = StringQualifier("ff_backup_phrase")
val stxForAllFeatureFlag = StringQualifier("ff_stx_all")
val plaidFeatureFlag = StringQualifier("ff_plaid")
val unifiedBalancesFlag = StringQualifier("ff_unified_balances")
val bindFeatureFlag = StringQualifier("ff_bind")
val superAppModeService = StringQualifier("super_app_mode_service")
val loqateFeatureFlag = StringQualifier("ff_loqate")
val buyRefreshQuoteFeatureFlag = StringQualifier("ff_buy_refresh_quote")
val cardRejectionCheckFeatureFlag = StringQualifier("ff_card_rejection_check")
val assetOrderingFeatureFlag = StringQualifier("ff_asset_list_ordering")
val cowboysPromoFeatureFlag = StringQualifier("ff_cowboys_promo")
val cardPaymentAsyncFeatureFlag = StringQualifier("ff_card_payment_async")
val rbFrequencyFeatureFlag = StringQualifier("ff_rb_frequency")
val quickFillSellSwapFeatureFlag = StringQualifier("ff_sell_swap_quickfill_frequency")
val rbExperimentFeatureFlag = StringQualifier("ff_rb_experiment")
val superappRedesignFeatureFlag = StringQualifier("android_ff_superapp_redesign")
val sessionIdFeatureFlag = StringQualifier("ff_x_session_id")
val sardineFeatureFlag = StringQualifier("ff_sardine")
val stakingAccountFeatureFlag = StringQualifier("ff_staking_account")
val hideDustFeatureFlag = StringQualifier("ff_hide_dust")
val googleWalletFeatureFlag = StringQualifier("ff_google_wallet")
val improvedPaymentUxFeatureFlag = StringQualifier("ff_improved_payment_ux")
val nabu = StringQualifier("nabu")
val status = StringQualifier("status")
val authOkHttpClient = StringQualifier("authOkHttpClient")
val kotlinApiRetrofit = StringQualifier("kotlin-api")
val explorerRetrofit = StringQualifier("explorer")
val everypayRetrofit = StringQualifier("everypay")
val apiRetrofit = StringQualifier("api")
val kotlinXApiRetrofit = StringQualifier("kotlinx-api")
val kotlinXCoinApiRetrofit = StringQualifier("kotlinx-coin-api")
val serializerExplorerRetrofit = StringQualifier("serializer_explorer")
val gbp = StringQualifier("GBP")
val usd = StringQualifier("USD")
val eur = StringQualifier("EUR")
val ars = StringQualifier("ARS")
val priorityFee = StringQualifier("Priority")
val regularFee = StringQualifier("Regular")
val bigDecimal = StringQualifier("BigDecimal")
val kotlinJsonConverterFactory = StringQualifier("KotlinJsonConverterFactory")
val kotlinJsonAssetTicker = StringQualifier("KotlinJsonAssetTicker")
val bigInteger = StringQualifier("BigInteger")
val interestLimits = StringQualifier("InterestLimits")
val kyc = StringQualifier("kyc")
val uniqueId = StringQualifier("unique_id")
val uniqueUserAnalytics = StringQualifier("unique_user_analytics")
val userAnalytics = StringQualifier("user_analytics")
val walletAnalytics = StringQualifier("wallet_analytics")
val embraceLogger = StringQualifier("embrace_logger")
val payloadScopeQualifier = named("Payload")
val ioDispatcher = named("io_dispatcher")
val defaultOrder = named("default_order")
val swapSourceOrder = named("swap_source_order")
val swapTargetOrder = named("swap_target_order")
val sellOrder = named("sell_order")
val buyOrder = named("buy_order")
