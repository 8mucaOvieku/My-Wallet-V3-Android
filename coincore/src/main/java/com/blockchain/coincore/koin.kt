package com.blockchain.coincore

import com.blockchain.coincore.bch.BchAsset
import com.blockchain.coincore.btc.BtcAsset
import com.blockchain.coincore.eth.EthAsset
import com.blockchain.coincore.fiat.FiatAsset
import com.blockchain.coincore.fiat.LinkedBanksFactory
import com.blockchain.coincore.impl.BackendNotificationUpdater
import com.blockchain.coincore.impl.EthHotWalletAddressResolver
import com.blockchain.coincore.impl.HotWalletService
import com.blockchain.coincore.impl.TxProcessorFactory
import com.blockchain.coincore.impl.txEngine.TransferQuotesEngine
import com.blockchain.coincore.loader.AssetCatalogueImpl
import com.blockchain.coincore.loader.AssetLoader
import com.blockchain.coincore.loader.DynamicAssetLoader
import com.blockchain.coincore.wrap.FormatUtilities
import com.blockchain.coincore.xlm.XlmAsset
import com.blockchain.koin.entitySwitchSilverEligibilityFeatureFlag
import com.blockchain.koin.ethMemoHotWalletFeatureFlag
import com.blockchain.koin.payloadScope
import com.blockchain.koin.payloadScopeQualifier
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.CryptoCurrency
import org.koin.dsl.bind
import org.koin.dsl.module

val coincoreModule = module {

    scope(payloadScopeQualifier) {

        scoped {
            BtcAsset(
                exchangeRates = get(),
                sendDataManager = get(),
                feeDataManager = get(),
                currencyPrefs = get(),
                payloadManager = get(),
                custodialManager = get(),
                tradingBalances = get(),
                interestBalances = get(),
                pitLinking = get(),
                crashLogger = get(),
                labels = get(),
                walletPreferences = get(),
                notificationUpdater = get(),
                coinsWebsocket = get(),
                identity = get(),
                entitySwitchSilverEligibilityFeatureFlag = get(entitySwitchSilverEligibilityFeatureFlag),
                addressResolver = get()
            )
        }.bind(CryptoAsset::class)

        scoped {
            BchAsset(
                payloadManager = get(),
                bchDataManager = get(),
                exchangeRates = get(),
                currencyPrefs = get(),
                crashLogger = get(),
                custodialManager = get(),
                interestBalances = get(),
                tradingBalances = get(),
                feeDataManager = get(),
                sendDataManager = get(),
                pitLinking = get(),
                labels = get(),
                walletPreferences = get(),
                beNotifyUpdate = get(),
                identity = get(),
                entitySwitchSilverEligibilityFeatureFlag = get(entitySwitchSilverEligibilityFeatureFlag),
                addressResolver = get()
            )
        }.bind(CryptoAsset::class)

        scoped {
            XlmAsset(
                payloadManager = get(),
                xlmDataManager = get(),
                xlmFeesFetcher = get(),
                walletOptionsDataManager = get(),
                exchangeRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                tradingBalances = get(),
                interestBalances = get(),
                pitLinking = get(),
                crashLogger = get(),
                labels = get(),
                walletPreferences = get(),
                identity = get(),
                entitySwitchSilverEligibilityFeatureFlag = get(entitySwitchSilverEligibilityFeatureFlag),
                addressResolver = get()
            )
        }.bind(CryptoAsset::class)

        scoped {
            EthAsset(
                payloadManager = get(),
                ethDataManager = get(),
                feeDataManager = get(),
                exchangeRates = get(),
                currencyPrefs = get(),
                walletPrefs = get(),
                crashLogger = get(),
                custodialManager = get(),
                tradingBalances = get(),
                interestBalances = get(),
                pitLinking = get(),
                labels = get(),
                notificationUpdater = get(),
                identity = get(),
                entitySwitchSilverEligibilityFeatureFlag = get(entitySwitchSilverEligibilityFeatureFlag),
                assetCatalogue = lazy { get() },
                formatUtils = get(),
                addressResolver = get()
            )
        }.bind(CryptoAsset::class)

        scoped {
            FiatAsset(
                labels = get(),
                tradingBalanceDataManager = get(),
                exchangeRateDataManager = get(),
                custodialWalletManager = get(),
                paymentsDataManager = get(),
                currencyPrefs = get()
            )
        }

        scoped {
            Coincore(
                assetCatalogue = get(),
                payloadManager = get(),
                fiatAsset = get<FiatAsset>(),
                assetLoader = get(),
                txProcessorFactory = get(),
                defaultLabels = get(),
                crashLogger = get(),
                paymentsDataManager = get(),
                currencyPrefs = get()
            )
        }

        scoped {
            val ncAssets: List<CryptoAsset> = payloadScope.getAll()
            // For some unknown reason `getAll()` adds the last element twice. Which means
            // that last element calls init() twice. So make it a set, to remove any duplicates.
            DynamicAssetLoader(
                nonCustodialAssets = ncAssets.toSet(),
                assetCatalogue = get(),
                payloadManager = get(),
                erc20DataManager = get(),
                feeDataManager = get(),
                exchangeRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                tradingBalances = get(),
                interestBalances = get(),
                crashLogger = get(),
                labels = get(),
                pitLinking = get(),
                walletPreferences = get(),
                identity = get(),
                entitySwitchSilverEligibilityFeatureFlag = get(entitySwitchSilverEligibilityFeatureFlag),
                formatUtils = get(),
                identityAddressResolver = get(),
                ethHotWalletAddressResolver = get()
            )
        }.bind(AssetLoader::class)

        scoped {
            HotWalletService(
                walletApi = get(),
                ethMemoForHotWalletFeatureFlag = get(ethMemoHotWalletFeatureFlag)
            )
        }

        scoped {
            IdentityAddressResolver()
        }

        scoped {
            EthHotWalletAddressResolver(
                hotWalletService = get()
            )
        }

        scoped {
            TxProcessorFactory(
                bitPayManager = get(),
                exchangeRates = get(),
                interestBalances = get(),
                walletManager = get(),
                paymentsDataManager = get(),
                ethMessageSigner = get(),
                limitsDataManager = get(),
                walletPrefs = get(),
                quotesEngine = get(),
                analytics = get(),
                fees = get(),
                ethDataManager = get(),
                bankPartnerCallbackProvider = get(),
                userIdentity = get(),
                withdrawLocksRepository = get()
            )
        }

        scoped {
            AddressFactoryImpl(
                coincore = get(),
                addressResolver = get()
            )
        }.bind(AddressFactory::class)

        scoped {
            BackendNotificationUpdater(
                prefs = get(),
                walletApi = get()
            )
        }

        factory {
            TransferQuotesEngine(quotesProvider = get())
        }

        factory {
            LinkedBanksFactory(
                custodialWalletManager = get(),
                paymentsDataManager = get()
            )
        }

        factory {
            SwapTrendingPairsProvider(
                coincore = get(),
                assetCatalogue = get(),
                identity = get()
            )
        }.bind(TrendingPairsProvider::class)
    }

    single {
        AssetCatalogueImpl(
            fixedAssets = nonCustodialAssetList(),
            assetsDataManager = get()
        )
    }.bind(AssetCatalogue::class)

    factory {
        FormatUtilities()
    }
}

fun nonCustodialAssetList() =
    setOf(
        CryptoCurrency.BTC,
        CryptoCurrency.BCH,
        CryptoCurrency.ETHER,
        CryptoCurrency.XLM
    )
