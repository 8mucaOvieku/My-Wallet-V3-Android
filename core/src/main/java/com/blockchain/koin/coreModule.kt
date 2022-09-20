package com.blockchain.koin

import android.content.Context
import android.preference.PreferenceManager
import com.blockchain.common.util.AndroidDeviceIdGenerator
import com.blockchain.core.Database
import com.blockchain.core.SwapTransactionsCache
import com.blockchain.core.TransactionsCache
import com.blockchain.core.asset.data.AssetRepository
import com.blockchain.core.asset.data.dataresources.AssetInformationStore
import com.blockchain.core.asset.domain.AssetService
import com.blockchain.core.buy.BuyOrdersCache
import com.blockchain.core.buy.BuyPairsCache
import com.blockchain.core.buy.data.SimpleBuyRepository
import com.blockchain.core.buy.data.dataresources.BuyPairsStore
import com.blockchain.core.buy.data.dataresources.SimpleBuyEligibilityStore
import com.blockchain.core.buy.domain.SimpleBuyService
import com.blockchain.core.chains.EvmNetworksService
import com.blockchain.core.chains.bitcoincash.BchBalanceCache
import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.core.chains.bitcoincash.BchDataStore
import com.blockchain.core.chains.dynamicselfcustody.data.NonCustodialRepository
import com.blockchain.core.chains.dynamicselfcustody.data.NonCustodialSubscriptionsStore
import com.blockchain.core.chains.dynamicselfcustody.domain.NonCustodialService
import com.blockchain.core.chains.erc20.Erc20DataManager
import com.blockchain.core.chains.erc20.Erc20DataManagerImpl
import com.blockchain.core.chains.erc20.call.Erc20HistoryCallCache
import com.blockchain.core.chains.erc20.data.Erc20L2StoreRepository
import com.blockchain.core.chains.erc20.data.Erc20StoreRepository
import com.blockchain.core.chains.erc20.data.store.Erc20DataSource
import com.blockchain.core.chains.erc20.data.store.Erc20L2DataSource
import com.blockchain.core.chains.erc20.data.store.Erc20L2Store
import com.blockchain.core.chains.erc20.data.store.Erc20Store
import com.blockchain.core.chains.erc20.data.store.L1BalanceStore
import com.blockchain.core.chains.erc20.domain.Erc20L2StoreService
import com.blockchain.core.chains.erc20.domain.Erc20StoreService
import com.blockchain.core.common.caching.StoreWiperImpl
import com.blockchain.core.custodial.BrokerageDataManager
import com.blockchain.core.custodial.data.TradingRepository
import com.blockchain.core.custodial.data.store.TradingStore
import com.blockchain.core.custodial.domain.TradingService
import com.blockchain.core.dataremediation.DataRemediationRepository
import com.blockchain.core.dynamicassets.DynamicAssetsDataManager
import com.blockchain.core.dynamicassets.impl.DynamicAssetsDataManagerImpl
import com.blockchain.core.eligibility.EligibilityRepository
import com.blockchain.core.eligibility.cache.ProductsEligibilityStore
import com.blockchain.core.fiatcurrencies.FiatCurrenciesRepository
import com.blockchain.core.history.data.datasources.PaymentTransactionHistoryStore
import com.blockchain.core.interest.data.InterestRepository
import com.blockchain.core.interest.data.datasources.InterestAvailableAssetsStore
import com.blockchain.core.interest.data.datasources.InterestBalancesStore
import com.blockchain.core.interest.data.datasources.InterestEligibilityStore
import com.blockchain.core.interest.data.datasources.InterestLimitsStore
import com.blockchain.core.interest.data.datasources.InterestRateStore
import com.blockchain.core.interest.domain.InterestService
import com.blockchain.core.limits.LimitsDataManager
import com.blockchain.core.limits.LimitsDataManagerImpl
import com.blockchain.core.nftwaitlist.data.NftWailslitRepository
import com.blockchain.core.nftwaitlist.domain.NftWaitlistService
import com.blockchain.core.payload.DataManagerPayloadDecrypt
import com.blockchain.core.payments.PaymentsRepository
import com.blockchain.core.payments.WithdrawLocksCache
import com.blockchain.core.payments.cache.LinkedCardsStore
import com.blockchain.core.payments.cache.PaymentMethodsEligibilityStore
import com.blockchain.core.referral.ReferralRepository
import com.blockchain.core.sdd.data.SddRepository
import com.blockchain.core.sdd.data.datasources.SddEligibilityStore
import com.blockchain.core.sdd.domain.SddService
import com.blockchain.core.user.NabuUserDataManager
import com.blockchain.core.user.NabuUserDataManagerImpl
import com.blockchain.core.user.WatchlistDataManager
import com.blockchain.core.user.WatchlistDataManagerImpl
import com.blockchain.domain.dataremediation.DataRemediationService
import com.blockchain.domain.eligibility.EligibilityService
import com.blockchain.domain.experiments.RemoteConfigService
import com.blockchain.domain.fiatcurrencies.FiatCurrenciesService
import com.blockchain.domain.paymentmethods.BankService
import com.blockchain.domain.paymentmethods.CardService
import com.blockchain.domain.paymentmethods.PaymentMethodService
import com.blockchain.domain.referral.ReferralService
import com.blockchain.logging.LastTxUpdateDateOnSettingsService
import com.blockchain.logging.LastTxUpdater
import com.blockchain.payload.PayloadDecrypt
import com.blockchain.preferences.AppInfoPrefs
import com.blockchain.preferences.AppMaintenancePrefs
import com.blockchain.preferences.AppRatingPrefs
import com.blockchain.preferences.AuthPrefs
import com.blockchain.preferences.BankLinkingPrefs
import com.blockchain.preferences.CowboysPrefs
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.DashboardPrefs
import com.blockchain.preferences.EducationalScreensPrefs
import com.blockchain.preferences.LocalSettingsPrefs
import com.blockchain.preferences.NftAnnouncementPrefs
import com.blockchain.preferences.NotificationPrefs
import com.blockchain.preferences.OnboardingPrefs
import com.blockchain.preferences.ReferralPrefs
import com.blockchain.preferences.RemoteConfigPrefs
import com.blockchain.preferences.SecureChannelPrefs
import com.blockchain.preferences.SecurityPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.preferences.WalletStatusPrefs
import com.blockchain.remoteconfig.RemoteConfigRepository
import com.blockchain.storedatasource.StoreWiper
import com.blockchain.sunriver.XlmHorizonUrlFetcher
import com.blockchain.sunriver.XlmTransactionTimeoutFetcher
import com.blockchain.wallet.SeedAccess
import com.blockchain.wallet.SeedAccessWithoutPrompt
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.payload.WalletPayloadService
import info.blockchain.wallet.util.PrivateKeyFactory
import java.util.UUID
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.androidcore.data.access.PinRepository
import piuk.blockchain.androidcore.data.access.PinRepositoryImpl
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.auth.WalletAuthService
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.ethereum.EthMessageSigner
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManagerSeedAccessAdapter
import piuk.blockchain.androidcore.data.payload.PayloadService
import piuk.blockchain.androidcore.data.payload.PromptingSeedAccessAdapter
import piuk.blockchain.androidcore.data.payments.PaymentService
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.SSLPinningEmitter
import piuk.blockchain.androidcore.data.rxjava.SSLPinningObservable
import piuk.blockchain.androidcore.data.rxjava.SSLPinningSubject
import piuk.blockchain.androidcore.data.settings.EmailSyncUpdater
import piuk.blockchain.androidcore.data.settings.PhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.data.settings.SettingsEmailAndSyncUpdater
import piuk.blockchain.androidcore.data.settings.SettingsPhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsService
import piuk.blockchain.androidcore.data.settings.datastore.SettingsDataStore
import piuk.blockchain.androidcore.data.settings.datastore.SettingsMemoryStore
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState
import piuk.blockchain.androidcore.utils.AESUtilWrapper
import piuk.blockchain.androidcore.utils.CloudBackupAgent
import piuk.blockchain.androidcore.utils.DeviceIdGenerator
import piuk.blockchain.androidcore.utils.DeviceIdGeneratorImpl
import piuk.blockchain.androidcore.utils.EncryptedPrefs
import piuk.blockchain.androidcore.utils.PrefsUtil
import piuk.blockchain.androidcore.utils.SessionPrefs
import piuk.blockchain.androidcore.utils.UUIDGenerator

val coreModule = module {

    single { RxBus() }

    single { SSLPinningSubject() }.apply {
        bind(SSLPinningObservable::class)
        bind(SSLPinningEmitter::class)
    }

    factory {
        WalletAuthService(
            walletApi = get()
        )
    }

    factory { PrivateKeyFactory() }

    scope(payloadScopeQualifier) {

        factory<DataRemediationService> {
            DataRemediationRepository(
                api = get(),
            )
        }

        scoped {
            TradingStore(
                balanceService = get(),
            )
        }

        scoped<TradingService> {
            TradingRepository(
                assetCatalogue = get(),
                tradingStore = get()
            )
        }

        scoped {
            BrokerageDataManager(
                brokerageService = get(),
            )
        }

        scoped {
            LimitsDataManagerImpl(
                limitsService = get(),
                exchangeRatesDataManager = get(),
                assetCatalogue = get(),
            )
        }.bind(LimitsDataManager::class)

        factory {
            ProductsEligibilityStore(
                productEligibilityApi = get()
            )
        }

        scoped {
            EligibilityRepository(
                productsEligibilityStore = get(),
                eligibilityApiService = get()
            )
        }.bind(EligibilityService::class)

        scoped {
            FiatCurrenciesRepository(
                getUserStore = get(),
                userService = get(),
                assetCatalogue = get(),
                currencyPrefs = get(),
                analytics = get(),
                api = get(),
            )
        }.bind(FiatCurrenciesService::class)

        scoped {
            InterestBalancesStore(
                interestApiService = get(),
            )
        }

        scoped {
            InterestAvailableAssetsStore(
                interestApiService = get(),
            )
        }

        scoped {
            InterestEligibilityStore(
                interestApiService = get(),
            )
        }

        scoped {
            InterestLimitsStore(
                interestApiService = get(),
                currencyPrefs = get()
            )
        }

        scoped {
            InterestRateStore(
                interestApiService = get(),
            )
        }

        scoped<InterestService> {
            InterestRepository(
                assetCatalogue = get(),
                interestBalancesStore = get(),
                interestEligibilityStore = get(),
                interestAvailableAssetsStore = get(),
                interestLimitsStore = get(),
                interestRateStore = get(),
                paymentTransactionHistoryStore = get(),
                currencyPrefs = get(),
                interestApiService = get()
            )
        }

        scoped {
            SddEligibilityStore(
                nabuService = get()
            )
        }

        scoped<SddService> {
            SddRepository(
                sddEligibilityStore = get()
            )
        }

        scoped {
            BuyPairsCache(nabuService = get())
        }

        scoped {
            BuyPairsStore(nabuService = get())
        }

        scoped {
            SimpleBuyEligibilityStore(
                nabuService = get()
            )
        }

        scoped<SimpleBuyService> {
            SimpleBuyRepository(
                simpleBuyEligibilityStore = get(),
                buyPairsStore = get()
            )
        }

        scoped {
            TransactionsCache(
                nabuService = get(),
            )
        }

        scoped {
            PaymentTransactionHistoryStore(
                nabuService = get(),
            )
        }

        scoped {
            SwapTransactionsCache(
                nabuService = get(),
            )
        }

        scoped {
            BuyOrdersCache(nabuService = get())
        }

        factory {
            EvmNetworksService(
                remoteConfig = get()
            )
        }

        scoped {
            EthDataManager(
                payloadDataManager = get(),
                ethAccountApi = get(),
                ethDataStore = get(),
                metadataRepository = get(),
                lastTxUpdater = get(),
                evmNetworksService = get(),
                nonCustodialEvmService = get(),
                labels = get()
            )
        }.bind(EthMessageSigner::class)

        scoped {
            L1BalanceStore(
                ethDataManager = get()
            )
        }

        scoped<Erc20DataSource> {
            Erc20Store(
                erc20Service = get(),
                ethDataManager = get()
            )
        }

        scoped<Erc20StoreService> {
            Erc20StoreRepository(
                assetCatalogue = get(),
                erc20DataSource = get()
            )
        }

        scoped<Erc20L2DataSource> {
            Erc20L2Store(
                evmService = get(),
                ethDataManager = get()
            )
        }

        scoped<Erc20L2StoreService> {
            Erc20L2StoreRepository(
                assetCatalogue = get(),
                ethDataManager = get(),
                erc20L2DataSource = get()
            )
        }

        factory {
            Erc20HistoryCallCache(
                ethDataManager = get(),
                erc20Service = get(),
                evmService = get(),
                assetCatalogue = get()
            )
        }

        scoped {
            Erc20DataManagerImpl(
                ethDataManager = get(),
                l1BalanceStore = get(),
                historyCallCache = get(),
                assetCatalogue = get(),
                erc20StoreService = get(),
                erc20DataSource = get(),
                erc20L2StoreService = get(),
                erc20L2DataSource = get(),
                ethLayerTwoFeatureFlag = get(ethLayerTwoFeatureFlag),
                evmWithoutL1BalanceFeatureFlag = get(evmWithoutL1BalanceFeatureFlag)
            )
        }.bind(Erc20DataManager::class)

        factory { BchDataStore() }

        scoped {
            BchDataManager(
                payloadDataManager = get(),
                bchDataStore = get(),
                bitcoinApi = get(),
                bchBalanceCache = get(),
                defaultLabels = get(),
                metadataRepository = get(),
                remoteLogger = get()
            )
        }

        scoped {
            BchBalanceCache(
                payloadDataManager = get()
            )
        }

        factory {
            PayloadService(
                payloadManager = get()
            )
        }

        factory {
            PayloadDataManager(
                payloadService = get(),
                privateKeyFactory = get(),
                bitcoinApi = get(),
                payloadManager = get(),
                remoteLogger = get()
            )
        }.bind(WalletPayloadService::class)

        factory {
            DataManagerPayloadDecrypt(
                payloadDataManager = get(),
                bchDataManager = get()
            )
        }.bind(PayloadDecrypt::class)

        factory { PromptingSeedAccessAdapter(PayloadDataManagerSeedAccessAdapter(get()), get()) }.apply {
            bind(SeedAccessWithoutPrompt::class)
            bind(SeedAccess::class)
        }

        scoped { EthDataStore() }

        scoped { WalletOptionsState() }

        scoped {
            SettingsDataManager(
                settingsService = get(),
                settingsDataStore = get(),
                currencyPrefs = get(),
                walletSettingsService = get(),
                assetCatalogue = get()
            )
        }

        scoped { SettingsService(get()) }

        scoped {
            SettingsDataStore(SettingsMemoryStore(), get<SettingsService>().getSettingsObservable())
        }

        factory {
            WalletOptionsDataManager(
                authService = get(),
                walletOptionsState = get(),
                settingsDataManager = get(),
                explorerUrl = getProperty("explorer-api")
            )
        }.apply {
            bind(XlmTransactionTimeoutFetcher::class)
            bind(XlmHorizonUrlFetcher::class)
        }

        scoped { FeeDataManager(get()) }

        factory {
            AuthDataManager(
                authApiService = get(),
                walletAuthService = get(),
                pinRepository = get(),
                aesUtilWrapper = get(),
                remoteLogger = get(),
                authPrefs = get(),
                walletStatusPrefs = get(),
                encryptedPrefs = get()
            )
        }

        factory { LastTxUpdateDateOnSettingsService(get()) }.bind(LastTxUpdater::class)

        factory {
            SendDataManager(
                paymentService = get(),
                lastTxUpdater = get()
            )
        }

        factory { SettingsPhoneNumberUpdater(get()) }.bind(PhoneNumberUpdater::class)

        factory { SettingsEmailAndSyncUpdater(get(), get()) }.bind(EmailSyncUpdater::class)

        scoped {
            NabuUserDataManagerImpl(
                nabuUserService = get(),
                kycService = get()
            )
        }.bind(NabuUserDataManager::class)

        scoped {
            LinkedCardsStore(
                paymentMethodsService = get()
            )
        }

        scoped {
            PaymentMethodsEligibilityStore(
                paymentMethodsService = get()
            )
        }

        scoped {
            WithdrawLocksCache(
                paymentsService = get(),
                currencyPrefs = get()
            )
        }

        scoped {
            PaymentsRepository(
                paymentsService = get(),
                paymentMethodsService = get(),
                tradingService = get(),
                simpleBuyPrefs = get(),
                googlePayManager = get(),
                environmentConfig = get(),
                withdrawLocksCache = get(),
                assetCatalogue = get(),
                linkedCardsStore = get(),
                fiatCurrenciesService = get(),
                googlePayFeatureFlag = get(googlePayFeatureFlag),
                plaidFeatureFlag = get(plaidFeatureFlag)
            )
        }.apply {
            bind(BankService::class)
            bind(CardService::class)
            bind(PaymentMethodService::class)
        }

        scoped {
            WatchlistDataManagerImpl(
                watchlistService = get(),
                assetCatalogue = get()
            )
        }.bind(WatchlistDataManager::class)

        factory {
            ReferralRepository(
                referralApi = get(),
                currencyPrefs = get(),
            )
        }.bind(ReferralService::class)

        scoped<NftWaitlistService> {
            NftWailslitRepository(
                nftWaitlistApiService = get(),
                userIdentity = get()
            )
        }

        scoped<NonCustodialService> {
            NonCustodialRepository(
                subscriptionsStore = get(),
                dynamicSelfCustodyService = get(),
                payloadDataManager = get(),
                currencyPrefs = get(),
                assetCatalogue = get(),
                remoteConfigService = get()
            )
        }

        scoped {
            NonCustodialSubscriptionsStore(
                dynamicSelfCustodyService = get(),
                authPrefs = get()
            )
        }
    }

    single {
        RemoteConfigRepository(
            firebaseRemoteConfig = get(),
            remoteConfigPrefs = get(),
            experimentsStore = get(),
            json = get(),
        )
    }.bind(RemoteConfigService::class)

    single {
        DynamicAssetsDataManagerImpl(
            discoveryService = get(),
        )
    }.bind(DynamicAssetsDataManager::class)

    single {
        AssetInformationStore(
            discoveryService = get()
        )
    }

    single<AssetService> {
        AssetRepository(
            assetInformationStore = get()
        )
    }

    factory {
        AndroidDeviceIdGenerator(
            ctx = get()
        )
    }

    factory {
        DeviceIdGeneratorImpl(
            platformDeviceIdGenerator = get(),
            analytics = get()
        )
    }.bind(DeviceIdGenerator::class)

    factory {
        object : UUIDGenerator {
            override fun generateUUID(): String = UUID.randomUUID().toString()
        }
    }.bind(UUIDGenerator::class)

    single {
        PrefsUtil(
            ctx = get(),
            store = get(),
            backupStore = CloudBackupAgent.backupPrefs(ctx = get()),
            idGenerator = get(),
            uuidGenerator = get(),
            assetCatalogue = get(),
            environmentConfig = get()
        )
    }.apply {
        bind(SessionPrefs::class)
        bind(CurrencyPrefs::class)
        bind(NotificationPrefs::class)
        bind(DashboardPrefs::class)
        bind(SecurityPrefs::class)
        bind(RemoteConfigPrefs::class)
        bind(SimpleBuyPrefs::class)
        bind(WalletStatusPrefs::class)
        bind(EncryptedPrefs::class)
        bind(AuthPrefs::class)
        bind(AppInfoPrefs::class)
        bind(BankLinkingPrefs::class)
        bind(SecureChannelPrefs::class)
        bind(OnboardingPrefs::class)
        bind(AppMaintenancePrefs::class)
        bind(AppRatingPrefs::class)
        bind(NftAnnouncementPrefs::class)
        bind(ReferralPrefs::class)
        bind(LocalSettingsPrefs::class)
        bind(EducationalScreensPrefs::class)
        bind(CowboysPrefs::class)
    }

    factory {
        PaymentService(
            payment = get(),
            dustService = get()
        )
    }

    factory {
        PreferenceManager.getDefaultSharedPreferences(
            /* context = */ get()
        )
    }

    factory(featureFlagsPrefs) {
        get<Context>().getSharedPreferences("FeatureFlagsPrefs", Context.MODE_PRIVATE)
    }

    single {
        PinRepositoryImpl()
    }.bind(PinRepository::class)

    factory { AESUtilWrapper() }

    single {
        Database(driver = get())
    }

    single<StoreWiper> {
        StoreWiperImpl(
            inMemoryCacheWiper = get(),
            persistedJsonSqlDelightCacheWiper = get()
        )
    }
}

fun experimentalL1EvmAssetList(): Set<CryptoCurrency> =
    setOf(CryptoCurrency.MATIC, CryptoCurrency.BNB)
