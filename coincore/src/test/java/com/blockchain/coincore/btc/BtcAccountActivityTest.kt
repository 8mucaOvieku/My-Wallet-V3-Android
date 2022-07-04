package com.blockchain.coincore.btc

import com.blockchain.coincore.TradeActivitySummaryItem
import com.blockchain.coincore.impl.AccountRefreshTrigger
import com.blockchain.coincore.testutil.CoincoreTestBase
import com.blockchain.coincore.testutil.USD
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.repositories.swap.TradeTransactionItem
import com.blockchain.preferences.WalletStatusPrefs
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.XPub
import info.blockchain.wallet.payload.data.XPubs
import io.reactivex.rxjava3.core.Single
import java.math.BigInteger
import org.junit.Before
import org.junit.Test
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager

class BtcAccountActivityTest : CoincoreTestBase() {

    private val payloadDataManager: PayloadDataManager = mock()
    private val sendDataManager: SendDataManager = mock()
    private val feeDataManager: FeeDataManager = mock()
    private val walletPrefs: WalletStatusPrefs = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val refreshTrigger: AccountRefreshTrigger = mock()

    private val jsonAccount: Account = mock {
        on { isArchived }.thenReturn(false)
        on { xpubs }.thenReturn(XPubs(listOf(XPub(ACCOUNT_XPUB, XPub.Format.LEGACY))))
    }

    private val subject =
        BtcCryptoWalletAccount(
            payloadDataManager = payloadDataManager,
            hdAccountIndex = -1,
            sendDataManager = sendDataManager,
            feeDataManager = feeDataManager,
            exchangeRates = exchangeRates,
            internalAccount = jsonAccount,
            isHDAccount = true,
            walletPreferences = walletPrefs,
            custodialWalletManager = custodialWalletManager,
            refreshTrigger = refreshTrigger,
            addressResolver = mock()
        )

    @Before
    fun setup() {
        initMocks()
    }

    // For NC accounts, Swaps are mapped into the activity stream if there is a matching SENT
    // on-chain event. Otherwise they are not.
    @Test
    fun fetchTransactionsOnAccountReceive() {
        val summary = TransactionSummary(
            confirmations = 3,
            transactionType = TransactionSummary.TransactionType.RECEIVED,
            fee = BigInteger.ONE,
            total = BigInteger.TEN,
            hash = TX_HASH_RECEIVE,
            inputsMap = emptyMap(),
            outputsMap = emptyMap(),
            inputsXpubMap = emptyMap(),
            outputsXpubMap = emptyMap(),
            time = 1000000L
        )

        val transactionSummaries = listOf(summary)
        val receivingValue = CryptoValue.zero(CryptoCurrency.ETHER)
        val sendingValue = CryptoValue.zero(CryptoCurrency.BTC)
        val swapSummary = TradeTransactionItem(
            txId = TX_HASH_SWAP,
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            "sendingAddress",
            "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = sendingValue,
            receivingValue = receivingValue,
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.ETHER),
            currencyPair = CurrencyPair(CryptoCurrency.BTC, CryptoCurrency.ETHER),
            apiFiatValue = FiatValue.zero(USD),
            price = mock()
        )

        val summaryList = listOf(swapSummary)

        whenever(payloadDataManager.getAccountTransactions(any(), any(), any()))
            .thenReturn(Single.just(transactionSummaries))
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValueAt(0) {
                val btcItem = it[0]

                it.size == 1 &&
                    btcItem is BtcActivitySummaryItem &&
                    btcItem.txId == summary.hash &&
                    btcItem.confirmations == summary.confirmations &&
                    btcItem.transactionType == summary.transactionType
            }

        verify(payloadDataManager).getAccountTransactions(any(), any(), any())
    }

    @Test
    fun fetchTransactionsOnAccountSendMatch() {

        val summary = TransactionSummary(
            confirmations = 3,
            transactionType = TransactionSummary.TransactionType.SENT,
            fee = BigInteger.ONE,
            total = BigInteger.TEN,
            hash = TX_HASH_SEND_MATCH,
            inputsMap = emptyMap(),
            outputsMap = emptyMap(),
            inputsXpubMap = emptyMap(),
            outputsXpubMap = emptyMap(),
            time = 1000000L
        )

        val transactionSummaries = listOf(summary)

        val receivingValue = CryptoValue.zero(CryptoCurrency.ETHER)
        val sendingValue = CryptoValue.zero(CryptoCurrency.BTC)
        val swapSummary = TradeTransactionItem(
            txId = TX_HASH_SWAP,
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            "sendingAddress",
            "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = sendingValue,
            receivingValue = receivingValue,
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.ETHER),
            currencyPair = CurrencyPair(CryptoCurrency.BTC, CryptoCurrency.ETHER),
            apiFiatValue = FiatValue.zero(TEST_USER_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)

        whenever(payloadDataManager.getAccountTransactions(any(), any(), any()))
            .thenReturn(Single.just(transactionSummaries))
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValueAt(0) {
                val swapItem = it[0]

                it.size == 1 &&
                    swapItem is TradeActivitySummaryItem &&
                    swapItem.txId == swapSummary.txId &&
                    swapItem.direction == swapSummary.direction &&
                    swapItem.currencyPair == CurrencyPair(
                    CryptoCurrency.BTC,
                    CryptoCurrency.ETHER
                ) &&
                    swapItem.sendingAddress == swapSummary.sendingAddress &&
                    swapItem.receivingAddress == swapSummary.receivingAddress &&
                    swapItem.state == swapSummary.state &&
                    swapItem.fiatValue == swapSummary.apiFiatValue &&
                    swapItem.fiatCurrency == TEST_USER_FIAT
            }

        verify(payloadDataManager).getAccountTransactions(any(), any(), any())
    }

    @Test
    fun fetchTransactionsOnAccountSendNoMatch() {

        val summary = TransactionSummary(
            confirmations = 3,
            transactionType = TransactionSummary.TransactionType.SENT,
            fee = BigInteger.ONE,
            total = BigInteger.TEN,
            hash = TX_HASH_SEND_NO_MATCH,
            inputsMap = emptyMap(),
            outputsMap = emptyMap(),
            inputsXpubMap = emptyMap(),
            outputsXpubMap = emptyMap(),
            time = 1000000L
        )

        val transactionSummaries = listOf(summary)

        val receivingValue = CryptoValue.zero(CryptoCurrency.ETHER)
        val sendingValue = CryptoValue.zero(CryptoCurrency.BTC)
        val swapSummary = TradeTransactionItem(
            txId = TX_HASH_SWAP,
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            "sendingAddress",
            "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = sendingValue,
            receivingValue = receivingValue,
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.ETHER),
            currencyPair = CurrencyPair(CryptoCurrency.BTC, CryptoCurrency.ETHER),
            apiFiatValue = FiatValue.zero(TEST_USER_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)

        whenever(payloadDataManager.getAccountTransactions(any(), any(), any()))
            .thenReturn(Single.just(transactionSummaries))
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValueAt(0) {
                val btcItem = it[0]

                it.size == 1 &&
                    btcItem is BtcActivitySummaryItem &&
                    btcItem.txId == summary.hash &&
                    btcItem.confirmations == summary.confirmations &&
                    btcItem.transactionType == summary.transactionType
            }

        verify(payloadDataManager).getAccountTransactions(any(), any(), any())
    }

    companion object {
        private const val TX_HASH_SEND_MATCH = "0x12345678890"
        private const val TX_HASH_SEND_NO_MATCH = "0x0987654321"
        private const val TX_HASH_RECEIVE = "0x12345678890"
        private const val TX_HASH_SWAP = "12345678890"
        private const val ACCOUNT_XPUB = "1234jfwepsdfapsksefksdwperoun894y98hefjbnakscdfoiw4rnwef"
    }
}
