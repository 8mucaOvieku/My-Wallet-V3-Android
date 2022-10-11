package com.blockchain.coincore.xlm

import com.blockchain.coincore.NonCustodialActivitySummaryItem
import com.blockchain.coincore.TradeActivitySummaryItem
import com.blockchain.coincore.testutil.CoincoreTestBase
import com.blockchain.core.walletoptions.WalletOptionsDataManager
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.repositories.swap.TradeTransactionItem
import com.blockchain.preferences.WalletStatusPrefs
import com.blockchain.sunriver.HorizonKeyPair
import com.blockchain.sunriver.Memo
import com.blockchain.sunriver.XlmAccountReference
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.XlmFeesFetcher
import com.blockchain.sunriver.models.XlmTransaction
import com.blockchain.testutils.stroops
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.rxjava3.core.Single
import java.math.BigInteger
import org.junit.Test
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class XlmAccountActivityTest : CoincoreTestBase() {

    private val payloadManager: PayloadDataManager = mock()

    private val xlmDataManager: XlmDataManager = mock()
    private val xlmFeesFetcher: XlmFeesFetcher = mock()
    private val walletOptionsDataManager: WalletOptionsDataManager = mock()
    private val walletPreferences: WalletStatusPrefs = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val xlmAccountReference = XlmAccountReference(
        label = "Test Xlm Account",
        accountId = "Test XLM Address"
    )

    private val subject =
        XlmCryptoWalletAccount(
            payloadManager = payloadManager,
            xlmAccountReference = xlmAccountReference,
            xlmManager = xlmDataManager,
            exchangeRates = exchangeRates,
            xlmFeesFetcher = xlmFeesFetcher,
            walletOptionsDataManager = walletOptionsDataManager,
            walletPreferences = walletPreferences,
            custodialWalletManager = custodialWalletManager,
            addressResolver = mock()
        )

    @Test
    fun getXlmTransactionListReceived() {
        // Arrange
        val output = BigInteger.valueOf(1000000L)
        val xlmTransaction = XlmTransaction(
            "2018-10-11T12:54:15Z",
            output.stroops(),
            100.stroops(),
            TX_HASH_RECEIVE,
            Memo.None,
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_1),
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_2)
        )

        whenever(xlmDataManager.getTransactionList())
            .thenReturn(Single.just(listOf(xlmTransaction)))

        val swapSummary = TradeTransactionItem(
            txId = TX_HASH_SWAP,
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            sendingAddress = "sendingAddress",
            receivingAddress = "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = CryptoValue.zero(CryptoCurrency.XLM),
            receivingValue = CryptoValue.zero(CryptoCurrency.BTC),
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.BTC),
            currencyPair = CurrencyPair(CryptoCurrency.XLM, CryptoCurrency.BTC),
            apiFiatValue = FiatValue.zero(TEST_API_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)
        whenever(
            custodialWalletManager.getCustodialActivityForAsset(
                CryptoCurrency.XLM,
                setOf(
                    TransferDirection.ON_CHAIN,
                    TransferDirection.FROM_USERKEY
                )
            )
        )
            .thenReturn(Single.just(summaryList))

        // Act
        subject.activity.test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValueAt(0) {
                val xlmItem = it[0]

                it.size == 1 &&
                    xlmItem is NonCustodialActivitySummaryItem &&
                    CryptoCurrency.XLM == xlmItem.asset &&
                    xlmTransaction.hash == xlmItem.txId &&
                    TransactionSummary.TransactionType.RECEIVED == xlmItem.transactionType &&
                    1 == xlmItem.confirmations &&
                    !xlmItem.isFeeTransaction &&
                    output == xlmItem.value.toBigInteger() &&
                    mapOf(
                        HORIZON_ACCOUNT_ID_2 to CryptoValue.fromMinor(
                            CryptoCurrency.XLM,
                            BigInteger.ZERO
                        )
                    ) == xlmItem.inputsMap &&
                    mapOf(
                        HORIZON_ACCOUNT_ID_1 to CryptoValue.fromMinor(
                            CryptoCurrency.XLM,
                            output
                        )
                    ) == xlmItem.outputsMap
            }

        verify(xlmDataManager).getTransactionList()
        verify(custodialWalletManager).getCustodialActivityForAsset(
            CryptoCurrency.XLM,
            setOf(
                TransferDirection.ON_CHAIN,
                TransferDirection.FROM_USERKEY
            )
        )
    }

    @Test
    fun getXlmTransactionListSendNoMatch() {
        // Arrange
        val value = 1000000.toBigInteger()
        val fee = 100.toBigInteger()
        val total = value + fee

        val xlmTransaction = XlmTransaction(
            "2018-10-11T12:54:15Z",
            value.unaryMinus().stroops(),
            fee.stroops(),
            TX_HASH_SEND_NO_MATCH,
            Memo.None,
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_1),
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_2)
        )

        whenever(xlmDataManager.getTransactionList())
            .thenReturn(Single.just(listOf(xlmTransaction)))

        val swapSummary = TradeTransactionItem(
            txId = TX_HASH_SWAP,
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            sendingAddress = "sendingAddress",
            receivingAddress = "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = CryptoValue.zero(CryptoCurrency.XLM),
            receivingValue = CryptoValue.zero(CryptoCurrency.BTC),
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.BTC),
            currencyPair = CurrencyPair(CryptoCurrency.XLM, CryptoCurrency.BTC),
            apiFiatValue = FiatValue.zero(TEST_API_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)
        whenever(
            custodialWalletManager.getCustodialActivityForAsset(
                CryptoCurrency.XLM,
                setOf(
                    TransferDirection.ON_CHAIN,
                    TransferDirection.FROM_USERKEY
                )
            )
        )
            .thenReturn(Single.just(summaryList))

        // Act
        subject.activity.test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValueAt(0) {
                val xlmItem = it[0]

                it.size == 1 &&
                    xlmItem is NonCustodialActivitySummaryItem &&
                    CryptoCurrency.XLM == xlmItem.asset &&
                    xlmTransaction.hash == xlmItem.txId &&
                    TransactionSummary.TransactionType.SENT == xlmItem.transactionType &&
                    1 == xlmItem.confirmations &&
                    !xlmItem.isFeeTransaction &&
                    total == xlmItem.value.toBigInteger() &&
                    mapOf(
                        HORIZON_ACCOUNT_ID_2 to
                            CryptoValue.fromMinor(CryptoCurrency.XLM, BigInteger.ZERO)
                    ) == xlmItem.inputsMap &&
                    mapOf(
                        HORIZON_ACCOUNT_ID_1 to
                            CryptoValue.fromMinor(CryptoCurrency.XLM, total)
                    ) == xlmItem.outputsMap
            }

        verify(xlmDataManager).getTransactionList()
        verify(custodialWalletManager).getCustodialActivityForAsset(
            CryptoCurrency.XLM,
            setOf(
                TransferDirection.ON_CHAIN,
                TransferDirection.FROM_USERKEY
            )
        )
    }

    @Test
    fun getXlmTransactionListSendMatch() {
        // Arrange
        val output = BigInteger.valueOf(-1000000L)
        val xlmTransaction = XlmTransaction(
            "2018-10-11T12:54:15Z",
            output.stroops(),
            100.stroops(),
            TX_HASH_SEND_MATCH,
            Memo.None,
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_1),
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_2)
        )

        whenever(xlmDataManager.getTransactionList())
            .thenReturn(Single.just(listOf(xlmTransaction)))

        val swapSummary = TradeTransactionItem(
            txId = TX_HASH_SWAP,
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            sendingAddress = "sendingAddress",
            receivingAddress = "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = CryptoValue.zero(CryptoCurrency.XLM),
            receivingValue = CryptoValue.zero(CryptoCurrency.BTC),
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.BTC),
            currencyPair = CurrencyPair(CryptoCurrency.XLM, CryptoCurrency.BTC),
            apiFiatValue = FiatValue.zero(TEST_API_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)
        whenever(
            custodialWalletManager.getCustodialActivityForAsset(
                CryptoCurrency.XLM,
                setOf(
                    TransferDirection.ON_CHAIN,
                    TransferDirection.FROM_USERKEY
                )
            )
        ).thenReturn(Single.just(summaryList))

        // Act
        subject.activity.test()
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
                    CryptoCurrency.XLM,
                    CryptoCurrency.BTC
                ) &&
                    swapItem.sendingAddress == swapSummary.sendingAddress &&
                    swapItem.receivingAddress == swapSummary.receivingAddress &&
                    swapItem.state == swapSummary.state &&
                    swapItem.fiatValue == FiatValue.zero(TEST_USER_FIAT) &&
                    swapItem.fiatCurrency == TEST_USER_FIAT
            }

        verify(xlmDataManager).getTransactionList()
        verify(custodialWalletManager).getCustodialActivityForAsset(
            CryptoCurrency.XLM,
            setOf(
                TransferDirection.ON_CHAIN,
                TransferDirection.FROM_USERKEY
            )
        )
    }

    companion object {
        private const val HORIZON_ACCOUNT_ID_1 =
            "GAIH3ULLFQ4DGSECF2AR555KZ4KNDGEKN4AFI4SU2M7B43MGK3QJZNSR"
        private const val HORIZON_ACCOUNT_ID_2 =
            "GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4"

        private const val TX_HASH_SEND_MATCH = "12345678890"
        private const val TX_HASH_SEND_NO_MATCH = "0987654321"
        private const val TX_HASH_RECEIVE = "12345678890"
        private const val TX_HASH_SWAP = "12345678890"
    }
}
