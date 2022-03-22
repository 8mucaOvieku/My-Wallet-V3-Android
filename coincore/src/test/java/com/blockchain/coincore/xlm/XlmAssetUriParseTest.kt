package com.blockchain.coincore.xlm

import com.blockchain.android.testutils.rxInit
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.interest.InterestBalanceDataManager
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.XlmFeesFetcher
import com.blockchain.wallet.DefaultLabels
import com.nhaarman.mockitokotlin2.mock
import io.reactivex.rxjava3.core.Single
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import thepit.PitLinking

class XlmAssetUriParseTest {

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val payloadManager: PayloadDataManager = mock()
    private val xlmDataManager: XlmDataManager = mock()
    private val xlmFeesFetcher: XlmFeesFetcher = mock()
    private val walletOptionsDataManager: WalletOptionsDataManager = mock()
    private val custodialManager: CustodialWalletManager = mock()
    private val exchangeRates: ExchangeRatesDataManager = mock()
    private val tradingBalances: TradingBalanceDataManager = mock()
    private val interestBalances: InterestBalanceDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val labels: DefaultLabels = mock()
    private val pitLinking: PitLinking = mock()
    private val crashLogger: CrashLogger = mock()
    private val walletPreferences: WalletStatus = mock()
    private val entitySwitchSilverEligibilityFeatureFlag: FeatureFlag = mock {
        on { enabled }.thenReturn(Single.just(false))
    }

    private val subject = XlmAsset(
        payloadManager = payloadManager,
        xlmDataManager = xlmDataManager,
        xlmFeesFetcher = xlmFeesFetcher,
        walletOptionsDataManager = walletOptionsDataManager,
        custodialManager = custodialManager,
        tradingBalances = tradingBalances,
        interestBalances = interestBalances,
        exchangeRates = exchangeRates,
        currencyPrefs = currencyPrefs,
        labels = labels,
        pitLinking = pitLinking,
        crashLogger = crashLogger,
        walletPreferences = walletPreferences,
        identity = mock(),
        addressResolver = mock()
    )

    @Test
    fun parseValidAddress() {

        val expectedResult = XlmAddress(
            _address = VALID_SCAN_URI,
            _label = VALID_SCAN_URI
        )

        subject.parseAddress(VALID_SCAN_URI)
            .test()
            .assertNoErrors()
            .assertResult(expectedResult)
    }

    @Test
    fun parseInvalidAddress() {

        subject.parseAddress(INVALID_SCAN_URI)
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    companion object {
        private const val VALID_SCAN_URI = "GDYULVJK2T6G7HFUC76LIBKZEMXPKGINSG6566EPWJKCLXTYVWJ7XPY4"
        private const val INVALID_SCAN_URI = "bitcoin:GDY6LIBKZEMXPKGINSG6566EPWJKCLXTYVWJ7XPY4"
    }
}
