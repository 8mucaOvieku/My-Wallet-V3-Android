package com.blockchain.coincore.eth

import com.blockchain.coincore.NonCustodialActivitySummaryItem
import com.blockchain.coincore.TradeActivitySummaryItem
import com.blockchain.coincore.testutil.CoincoreTestBase
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.repositories.swap.TradeTransactionItem
import com.blockchain.preferences.WalletStatus
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.wallet.ethereum.EthereumAccount
import info.blockchain.wallet.ethereum.data.EthLatestBlockNumber
import info.blockchain.wallet.ethereum.data.EthTransaction
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.spy
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.ethereum.models.CombinedEthModel
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class EthAccountActivityTest : CoincoreTestBase() {

    private val payloadManager: PayloadDataManager = mock()
    private val ethDataManager: EthDataManager = mock()
    private val feeDataManager: FeeDataManager = mock()
    private val walletPrefs: WalletStatus = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val ethAccount: EthereumAccount = mock {
        on { address }.thenReturn(ETH_ADDRESS)
        on { label }.thenReturn("TestEthAccount")
    }

    private val subject =
        spy(
            EthCryptoWalletAccount(
                payloadManager = payloadManager,
                jsonAccount = ethAccount,
                ethDataManager = ethDataManager,
                fees = feeDataManager,
                exchangeRates = exchangeRates,
                walletPreferences = walletPrefs,
                custodialWalletManager = custodialWalletManager,
                assetCatalogue = mock(),
                identity = mock(),
                addressResolver = mock()
            )
        )

    @Before
    fun setup() {
        initMocks()
    }

    @Test
    fun fetchTransactionsEthereumReplaceSendWithSwap() {
        // Arrange
        val latestBlock = EthLatestBlockNumber()
        val transaction = EthTransaction(
            hash = "hash",
            from = ETH_ADDRESS,
            to = "122345"
        )

        val ethModel: CombinedEthModel = mock()

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(latestBlock))

        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(transaction)))

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(ethModel)

        val swapSummary = TradeTransactionItem(
            txId = "hash",
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            sendingAddress = "sendingAddress",
            receivingAddress = "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = CryptoValue.zero(CryptoCurrency.ETHER),
            receivingValue = CryptoValue.zero(CryptoCurrency.BTC),
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.BTC),
            currencyPair = CurrencyPair(CryptoCurrency.ETHER, CryptoCurrency.BTC),
            apiFiatValue = FiatValue.zero(TEST_USER_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        doReturn(false).`when`(subject).isErc20FeeTransaction(any())

        subject.activity
            .test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1 &&
                    it[0].run {
                        this is TradeActivitySummaryItem &&
                            txId == swapSummary.txId &&
                            direction == swapSummary.direction &&
                            currencyPair == CurrencyPair(
                            CryptoCurrency.ETHER,
                            CryptoCurrency.BTC
                        ) &&
                            sendingAddress == swapSummary.sendingAddress &&
                            receivingAddress == swapSummary.receivingAddress &&
                            state == swapSummary.state &&
                            fiatValue == swapSummary.apiFiatValue &&
                            fiatCurrency == TEST_USER_FIAT
                    }
            }

        verify(ethDataManager).getLatestBlockNumber()
        verify(ethDataManager).getEthTransactions()
    }

    @Test
    fun fetchTransactionsEthereumReceive() {
        // Arrange
        val latestBlock = EthLatestBlockNumber()
        val transaction: EthTransaction = mock()

        val to = "12345"
        whenever(transaction.hash).thenReturn("hash")
        whenever(transaction.to).thenReturn(to)

        val ethModel: CombinedEthModel = mock()

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(latestBlock))

        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(transaction)))

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(ethModel)

        val swapSummary = TradeTransactionItem(
            txId = "123",
            timeStampMs = 1L,
            direction = TransferDirection.ON_CHAIN,
            sendingAddress = "sendingAddress",
            receivingAddress = "receivingAddress",
            state = CustodialOrderState.FINISHED,
            sendingValue = CryptoValue.zero(CryptoCurrency.ETHER),
            receivingValue = CryptoValue.zero(CryptoCurrency.BTC),
            withdrawalNetworkFee = CryptoValue.zero(CryptoCurrency.BTC),
            currencyPair = CurrencyPair(CryptoCurrency.ETHER, CryptoCurrency.BTC),
            apiFiatValue = FiatValue.zero(TEST_USER_FIAT),
            price = mock()
        )

        val summaryList = listOf(swapSummary)
        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(summaryList))

        doReturn(false).`when`(subject).isErc20FeeTransaction(to)

        subject.activity
            .test()
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1 &&
                    it[0].run {
                        this is EthActivitySummaryItem
                    }
            }

        verify(ethDataManager).getLatestBlockNumber()
        verify(ethDataManager).getEthTransactions()
    }

    @Test
    fun getEthTransactionsListWithOneErc20FeeTransactionInTheList() {
        // Arrange
        val ethTransaction = EthTransaction(
            to = "0x8E870D67F660D95d5be530380D0eC0bd388289E1"
        )

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(EthLatestBlockNumber()))

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(mock())
        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(ethTransaction)))

        doReturn(true).`when`(subject).isErc20FeeTransaction(ethTransaction.to)

        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(emptyList()))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1
            }
            .assertValue {
                (it[0] as NonCustodialActivitySummaryItem).isFeeTransaction
            }

        verify(ethDataManager).getEthTransactions()
    }

    @Test
    fun getEthTransactionsListWithNoErc20FeeTransactionInTheList() {
        // Arrange
        val ethTransaction = EthTransaction(
            to = "0x8E870D67F660D95d5be530380D0eC0bd388289E1"
        )

        whenever(ethDataManager.getLatestBlockNumber())
            .thenReturn(Single.just(EthLatestBlockNumber()))
        doReturn(false).`when`(subject).isErc20FeeTransaction(ethTransaction.to)

        whenever(ethDataManager.getEthResponseModel())
            .thenReturn(mock())
        whenever(ethDataManager.getEthTransactions())
            .thenReturn(Single.just(listOf(ethTransaction)))

        whenever(custodialWalletManager.getCustodialActivityForAsset(any(), any()))
            .thenReturn(Single.just(emptyList()))

        subject.activity
            .test()
            .assertValueCount(1)
            .assertComplete()
            .assertNoErrors()
            .assertValue {
                it.size == 1
            }
            .assertValue {
                !(it[0] as NonCustodialActivitySummaryItem).isFeeTransaction
            }

        verify(ethDataManager).getEthTransactions()
    }

    companion object {
        private const val ETH_ADDRESS = "test_address"
    }
}
