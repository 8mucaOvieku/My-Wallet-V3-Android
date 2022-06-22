package com.blockchain.core.interest.store

import com.blockchain.api.services.InterestBalanceDetails
import com.blockchain.core.interest.InterestAccountBalance
import com.blockchain.core.interest.data.InterestStoreRepository
import com.blockchain.core.interest.data.store.InterestDataSource
import com.blockchain.core.interest.domain.InterestStoreService
import com.blockchain.store.StoreResponse
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetCategory
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test

class InterestStoreServiceTest {
    private val assetCatalogue = mockk<AssetCatalogue>()
    private val interestDataSource = mockk<InterestDataSource>()

    private val interestStoreService: InterestStoreService = InterestStoreRepository(
        assetCatalogue = assetCatalogue,
        interestDataSource = interestDataSource
    )

    private val cryptoCurrency = object : CryptoCurrency(
        displayTicker = "CRYPTO1",
        networkTicker = "CRYPTO1",
        name = "Crypto_1",
        categories = setOf(AssetCategory.CUSTODIAL, AssetCategory.NON_CUSTODIAL),
        precisionDp = 8,
        requiredConfirmations = 5,
        colour = "#123456"
    ) {}

    private val interestBalanceDetails = InterestBalanceDetails(
        assetTicker = "CRYPTO1",
        totalBalance = 1.toBigInteger(),
        pendingInterest = 2.toBigInteger(),
        pendingDeposit = 3.toBigInteger(),
        totalInterest = 4.toBigInteger(),
        pendingWithdrawal = 5.toBigInteger(),
        lockedBalance = 6.toBigInteger()
    )

    private val interestAccountBalance = InterestAccountBalance(
        totalBalance = CryptoValue.fromMinor(cryptoCurrency, 1.toBigInteger()),
        pendingInterest = CryptoValue.fromMinor(cryptoCurrency, 2.toBigInteger()),
        pendingDeposit = CryptoValue.fromMinor(cryptoCurrency, 3.toBigInteger()),
        totalInterest = CryptoValue.fromMinor(cryptoCurrency, 4.toBigInteger()),
        lockedBalance = CryptoValue.fromMinor(cryptoCurrency, 6.toBigInteger()),
        hasTransactions = true
    )

    private val data = mapOf(cryptoCurrency to interestAccountBalance)

    @Before
    fun setUp() {
        every { interestDataSource.stream(any()) } returns flowOf(StoreResponse.Data(listOf(interestBalanceDetails)))
        every { interestDataSource.invalidate() } just Runs

        every { assetCatalogue.fromNetworkTicker("CRYPTO1") } returns cryptoCurrency
    }

    @Test
    fun testGetBalances() {
        interestStoreService.getBalances()
            .test()
            .await()
            .assertValue {
                it == data
            }
        verify(exactly = 1) { interestDataSource.stream(true) }
        verify(exactly = 1) { assetCatalogue.fromNetworkTicker("CRYPTO1") }
    }

    @Test
    fun testGetBalanceFor() {
        interestStoreService.getBalanceFor(cryptoCurrency)
            .test()
            .await()
            .assertValue {
                it == interestAccountBalance
            }
        verify(exactly = 1) { interestDataSource.stream(true) }
        verify(exactly = 1) { assetCatalogue.fromNetworkTicker("CRYPTO1") }
    }

    @Test
    fun testGetActiveAssets() {
        interestStoreService.getActiveAssets()
            .test()
            .await()
            .assertValue {
                it == setOf(cryptoCurrency)
            }
        verify(exactly = 1) { interestDataSource.stream(false) }
        verify(exactly = 1) { assetCatalogue.fromNetworkTicker("CRYPTO1") }
    }

    @Test
    fun testInvalidate() {
        interestStoreService.invalidate()
        verify(exactly = 1) { interestDataSource.invalidate() }
    }
}
