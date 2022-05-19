package com.blockchain.coincore.btc

import com.blockchain.android.testutils.rxInit
import com.blockchain.coincore.impl.BackendNotificationUpdater
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.interest.InterestBalanceDataManager
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.logging.RemoteLogger
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.NabuUserIdentity
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.wallet.DefaultLabels
import com.blockchain.websocket.CoinsWebSocketInterface
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import exchange.ExchangeLinking
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.keys.SigningKey
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.ImportedAddress
import info.blockchain.wallet.payload.data.XPub
import info.blockchain.wallet.payload.data.XPubs
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.bitcoinj.crypto.BIP38PrivateKey.BadPassphraseException
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager

class BtcAssetTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val payloadManager: PayloadDataManager = mock()
    private val sendDataManager: SendDataManager = mock()
    private val feeDataManager: FeeDataManager = mock()
    private val coinsWebsocket: CoinsWebSocketInterface = mock()
    private val custodialManager: CustodialWalletManager = mock()
    private val exchangeRates: ExchangeRatesDataManager = mock()
    private val tradingBalances: TradingBalanceDataManager = mock()
    private val interestBalances: InterestBalanceDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val labels: DefaultLabels = mock()
    private val exchangeLinking: ExchangeLinking = mock()
    private val remoteLogger: RemoteLogger = mock()
    private val walletPreferences: WalletStatus = mock()
    private val identity: NabuUserIdentity = mock()
    private val notificationUpdater: BackendNotificationUpdater = mock()

    private val subject = BtcAsset(
        payloadManager = payloadManager,
        sendDataManager = sendDataManager,
        feeDataManager = feeDataManager,
        coinsWebsocket = coinsWebsocket,
        custodialManager = custodialManager,
        tradingBalances = tradingBalances,
        interestBalances = interestBalances,
        exchangeRates = exchangeRates,
        currencyPrefs = currencyPrefs,
        labels = labels,
        exchangeLinking = exchangeLinking,
        remoteLogger = remoteLogger,
        notificationUpdater = notificationUpdater,
        walletPreferences = walletPreferences,
        identity = identity,
        addressResolver = mock()
    )

    @Test
    fun createAccountSuccessNoSecondPassword() {
        val mockInternalAccount: Account = mock {
            on { xpubs }.thenReturn(XPubs(listOf(XPub(NEW_XPUB, XPub.Format.LEGACY))))
        }

        whenever(payloadManager.createNewAccount(TEST_LABEL, null)).thenReturn(
            Observable.just(mockInternalAccount)
        )
        whenever(payloadManager.accountCount).thenReturn(NUM_ACCOUNTS)

        subject.createAccount(TEST_LABEL, null)
            .test()
            .assertValue {
                it.isHDAccount &&
                    !it.isArchived &&
                    it.xpubAddress == NEW_XPUB
            }.assertComplete()

        verify(coinsWebsocket).subscribeToXpubBtc(NEW_XPUB)
    }

    @Test
    fun createAccountFailed() {
        whenever(payloadManager.createNewAccount(TEST_LABEL, null)).thenReturn(
            Observable.error(Exception("Something went wrong"))
        )

        subject.createAccount(TEST_LABEL, null)
            .test()
            .assertError(Exception::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun importNonBip38Success() {
        val ecKey: SigningKey = mock {
            on { hasPrivKey }.thenReturn(true)
        }

        val internalAccount: ImportedAddress = mock {
            on { address }.thenReturn(IMPORTED_ADDRESS)
        }

        whenever(payloadManager.getKeyFromImportedData(NON_BIP38_FORMAT, KEY_DATA))
            .thenReturn(Single.just(ecKey))
        whenever(payloadManager.addImportedAddressFromKey(ecKey, null))
            .thenReturn(Single.just(internalAccount))

        subject.importAddressFromKey(KEY_DATA, NON_BIP38_FORMAT, null, null)
            .test()
            .assertValue {
                !it.isHDAccount &&
                    !it.isArchived &&
                    it.xpubAddress == IMPORTED_ADDRESS
            }.assertComplete()

        verify(coinsWebsocket).subscribeToExtraBtcAddress(IMPORTED_ADDRESS)
    }

    @Test
    fun importNonBip38NoPrivateKey() {
        val ecKey: SigningKey = mock {
            on { hasPrivKey }.thenReturn(true)
        }

        whenever(payloadManager.getKeyFromImportedData(NON_BIP38_FORMAT, KEY_DATA))
            .thenReturn(Single.just(ecKey))

        subject.importAddressFromKey(KEY_DATA, NON_BIP38_FORMAT, null, null)
            .test()
            .assertError(Exception::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun importNonBip38InvalidFormat() {
        whenever(payloadManager.getKeyFromImportedData(NON_BIP38_FORMAT, KEY_DATA))
            .thenReturn(Single.error(Exception()))

        subject.importAddressFromKey(KEY_DATA, NON_BIP38_FORMAT, null, null)
            .test()
            .assertError(Exception::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun importBip38Success() {
        val ecKey: SigningKey = mock {
            on { hasPrivKey }.thenReturn(true)
        }

        val internalAccount: ImportedAddress = mock {
            on { address }.thenReturn(IMPORTED_ADDRESS)
        }

        whenever(payloadManager.getBip38KeyFromImportedData(KEY_DATA, KEY_PASSWORD))
            .thenReturn(Single.just(ecKey))
        whenever(payloadManager.addImportedAddressFromKey(ecKey, null))
            .thenReturn(Single.just(internalAccount))

        subject.importAddressFromKey(KEY_DATA, BIP38_FORMAT, KEY_PASSWORD, null)
            .test()
            .assertValue {
                !it.isHDAccount &&
                    !it.isArchived &&
                    it.xpubAddress == IMPORTED_ADDRESS
            }.assertComplete()

        verify(coinsWebsocket).subscribeToExtraBtcAddress(IMPORTED_ADDRESS)
    }

    @Test
    fun importBip38BadPassword() {
        whenever(payloadManager.getBip38KeyFromImportedData(KEY_DATA, KEY_PASSWORD))
            .thenReturn(Single.error(BadPassphraseException()))

        subject.importAddressFromKey(KEY_DATA, BIP38_FORMAT, KEY_PASSWORD, null)
            .test()
            .assertError(BadPassphraseException::class.java)

        verifyNoMoreInteractions(coinsWebsocket)
    }

    @Test
    fun parseGoodAddressWithNoPrefix() {
        val goodAddress = "17GBRdfBHtEaBs7MesvMgob6YUEn5fFN4C"

        whenever(sendDataManager.isValidBtcAddress(goodAddress)).thenReturn(true)

        subject.parseAddress(goodAddress, TEST_LABEL)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { v ->
                v is BtcAddress &&
                    v.address == goodAddress &&
                    v.amount == null &&
                    v.label == TEST_LABEL
            }
    }

    @Test
    fun parseGoodAddressWithPrefix() {
        val prefix = "bitcoin:"
        val goodAddress = "17GBRdfBHtEaBs7MesvMgob6YUEn5fFN4C"
        val testInput = "$prefix$goodAddress"

        whenever(sendDataManager.isValidBtcAddress(goodAddress)).thenReturn(true)

        subject.parseAddress(testInput, TEST_LABEL)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { v ->
                v is BtcAddress &&
                    v.address == goodAddress &&
                    v.amount == null &&
                    v.label == TEST_LABEL
            }
    }

    @Test
    fun parseGoodAddressWithPrefixAndAmount() {
        val prefix = "bitcoin:"
        val goodAddress = "17GBRdfBHtEaBs7MesvMgob6YUEn5fFN4C"
        val amount = CryptoValue.fromMajor(CryptoCurrency.BTC, 0.004409.toBigDecimal())
        val testInput = "$prefix$goodAddress?amount=${amount.toStringWithoutSymbol()}"

        whenever(sendDataManager.isValidBtcAddress(goodAddress)).thenReturn(true)

        subject.parseAddress(testInput, TEST_LABEL)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { v ->
                v is BtcAddress &&
                    v.address == goodAddress &&
                    v.amount == amount &&
                    v.label == TEST_LABEL
            }
    }

    @Test
    fun parseGoodAddressWithPrefixAndUnknownSuffix() {

        val prefix = "bitcoin:"
        val goodAddress = "17GBRdfBHtEaBs7MesvMgob6YUEn5fFN4C"
        val amount = CryptoValue.fromMajor(CryptoCurrency.BTC, 0.004409.toBigDecimal())
        val testInput = "$prefix$goodAddress?unknown=${amount.toStringWithoutSymbol()}"

        whenever(sendDataManager.isValidBtcAddress(goodAddress)).thenReturn(true)

        subject.parseAddress(testInput, TEST_LABEL)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { v ->
                v is BtcAddress &&
                    v.address == goodAddress &&
                    v.amount == null &&
                    v.label == TEST_LABEL
            }
    }

    @Test
    fun parseGoodAddressWithPrefixAndAmountAndUnknownSuffix() {

        val prefix = "bitcoin:"
        val goodAddress = "17GBRdfBHtEaBs7MesvMgob6YUEn5fFN4C"
        val amount = CryptoValue.fromMajor(CryptoCurrency.BTC, 0.004409.toBigDecimal())
        val testInput = "$prefix$goodAddress?amount=${amount.toStringWithoutSymbol()}?unknown=whatever"

        whenever(sendDataManager.isValidBtcAddress(goodAddress)).thenReturn(true)

        subject.parseAddress(testInput, TEST_LABEL)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { v ->
                v is BtcAddress &&
                    v.address == goodAddress &&
                    v.amount == amount &&
                    v.label == TEST_LABEL
            }
    }

    @Test
    fun parseBadAddress() {
        val badAddress = "ThisIsNotABTCAddress"
        whenever(sendDataManager.isValidBtcAddress(badAddress)).thenReturn(false)

        subject.parseAddress(badAddress, TEST_LABEL)
            .test()
            .assertResult() // Should be empty
    }

    companion object {
        private const val TEST_LABEL = "TestLabel"
        private const val NEW_XPUB = "jaohaeoufoaehfoiuaehfiuhaefiuaeifuhaeifuh"
        private const val NUM_ACCOUNTS = 5

        private const val KEY_DATA = "aefouaoefkajdfsnkajsbkjasbdfkjbaskjbasfkj"
        private const val NON_BIP38_FORMAT = PrivateKeyFactory.BASE64
        private const val BIP38_FORMAT = PrivateKeyFactory.BIP38
        private const val KEY_PASSWORD = "SuperSecurePassword"
        private const val IMPORTED_ADDRESS = "aeoiawfohiawiawiohawdfoihawdhioadwfohiafwoih"
    }
}
