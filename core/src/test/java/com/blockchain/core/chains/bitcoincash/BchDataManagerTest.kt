@file:Suppress("SameParameterValue")
package com.blockchain.core.chains.bitcoincash

import com.blockchain.android.testutils.rxInit
import com.blockchain.api.services.NonCustodialBitcoinService
import com.blockchain.logging.CrashLogger
import com.blockchain.remoteconfig.IntegratedFeatureFlag
import com.blockchain.wallet.DefaultLabels
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.wallet.coin.GenericMetadataAccount
import info.blockchain.wallet.coin.GenericMetadataWallet
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.Derivation
import info.blockchain.wallet.payload.data.Wallet
import info.blockchain.wallet.payload.data.WalletBody
import info.blockchain.wallet.payload.data.XPub
import info.blockchain.wallet.payload.data.XPubs
import info.blockchain.wallet.payload.model.Balance
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.math.BigInteger
import junit.framework.Assert
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class BchDataManagerTest {

    @get:Rule
    val rxSchedulers = rxInit {
        ioTrampoline()
    }

    private lateinit var subject: BchDataManager

    private val payloadDataManager: PayloadDataManager = mock()
    private var bchDataStore: BchDataStore = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val crashLogger: CrashLogger = mock()
    private val bitcoinApi: NonCustodialBitcoinService = mock()
    private val defaultLabels: DefaultLabels = mock()
    private val metadataManager: MetadataManager = mock()
    private val kotlinSerializerFeatureFlag: IntegratedFeatureFlag = mock()

    @Before
    fun setUp() {
        subject = BchDataManager(
            payloadDataManager,
            bchDataStore,
            bitcoinApi,
            defaultLabels,
            metadataManager,
            crashLogger,
            kotlinSerializerFeatureFlag
        )
    }

    private fun mockAbsentMetadata() {
        whenever(metadataManager.fetchMetadata(any())).thenReturn(Maybe.empty())
    }

    private fun mockSingleMetadata(): String {
        val metaData = GenericMetadataWallet()
        val account = GenericMetadataAccount()
        account.label = "account label"
        metaData.addAccount(account)
        val withKotlinX = true

        whenever(metadataManager.fetchMetadata(any())).thenReturn(
            Maybe.just(
                metaData.toJson(withKotlinX)
            )
        )

        return metaData.toJson(withKotlinX)
    }

    private fun mockRestoringSingleBchWallet(xpub: String): GenericMetadataWallet {

        val mnemonic = split("all all all all all all all all all all all all")
        whenever(payloadDataManager.mnemonic).thenReturn(mnemonic)

        // 1 account
        val btcAccount: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.accounts).thenReturn(mutableListOf(btcAccount))
        whenever(btcAccount.xpubForDerivation(Derivation.LEGACY_TYPE)).thenReturn(xpub)

        val metaData: GenericMetadataWallet = mock()
        val bchMetaDataAccount: GenericMetadataAccount = mock()
        whenever(metaData.accounts).thenReturn(mutableListOf(bchMetaDataAccount))

        return metaData
    }

    @Test
    fun clearEthAccountDetails() {
        // Arrange

        // Act
        subject.clearAccountDetails()
        // Assert
        verify(bchDataStore).clearData()
        verifyNoMoreInteractions(bchDataStore)
    }

    @Test
    fun `initBchWallet create new metadata payload wo second pw`() {
        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(false)
        mockAbsentMetadata()
        mockRestoringSingleBchWallet("xpub")
        val withKotlinX = true

        whenever(bchDataStore.bchMetadata!!.toJson(withKotlinX)).thenReturn("{}")
        whenever(metadataManager.saveToMetadata(any(), any())).thenReturn(Completable.complete())
        whenever(kotlinSerializerFeatureFlag.enabled).thenReturn(Single.just(withKotlinX))

        // Act
        val testObserver = subject.initBchWallet("Bitcoin cash account").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun `initBchWallet retrieve existing data payload wo second pw`() {
        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(false)
        mockSingleMetadata()
        mockRestoringSingleBchWallet("xpub")
        whenever(defaultLabels.getDefaultNonCustodialWalletLabel()).thenReturn("label")
        whenever(kotlinSerializerFeatureFlag.enabled).thenReturn(Single.just(true))

        // Act
        val testObserver = subject.initBchWallet("Bitcoin cash account").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun `initBchWallet create new metadata payload with second pw`() {
        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(true)

        // Arrange
        mockAbsentMetadata()
        mockRestoringSingleBchWallet("xpub")
        val withKotlinX = true

        whenever(bchDataStore.bchMetadata!!.toJson(withKotlinX)).thenReturn("{}")
        whenever(metadataManager.saveToMetadata(any(), any())).thenReturn(Completable.complete())
        whenever(kotlinSerializerFeatureFlag.enabled).thenReturn(Single.just(withKotlinX))

        // Act
        val testObserver = subject.initBchWallet("Bitcoin cash account").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun `initBchWallet retrieve existing data payload with second pw`() {
        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(true)
        mockSingleMetadata()
        mockRestoringSingleBchWallet("xpub")
        whenever(defaultLabels.getDefaultNonCustodialWalletLabel()).thenReturn("label")
        whenever(kotlinSerializerFeatureFlag.enabled).thenReturn(Single.just(true))

        // Act
        val testObserver = subject.initBchWallet("Bitcoin cash account").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun `fetchMetadata doesn't exist`() {

        // Arrange
        mockAbsentMetadata()
        whenever(kotlinSerializerFeatureFlag.enabled).thenReturn(Single.just(true))

        // Act
        val testObserver = subject.fetchMetadata("label", 1).isEmpty.test()

        // Assert
        testObserver.assertValueAt(0, true)
        testObserver.assertComplete()
    }

    @Test
    fun `fetchMetadata exists`() {

        // Arrange
        val walletJson = mockSingleMetadata()
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.enabled).thenReturn(Single.just(withKotlinX))

        // Act
        val testObserver = subject.fetchMetadata("label", 1).test()

        // Assert
        testObserver.assertComplete()
        Assert.assertEquals(walletJson, testObserver.values()[0].toJson(withKotlinX))
    }

    @Test
    fun `restoreBchWallet with 2nd pw 1 account`() {

        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(true)
        val xpub = "xpub"
        val metaData = mockRestoringSingleBchWallet(xpub)

        // Act
        subject.restoreBchWallet(metaData)

        // Assert
        verify(bchDataStore.bchWallet)!!.addWatchOnlyAccount(xpub)
        verify(metaData.accounts[0]).setXpub(xpub)
    }

    @Test
    fun `restoreBchWallet with 2nd pw 2 account`() {

        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(true)
        val mnemonic = split("all all all all all all all all all all all all")
        whenever(payloadDataManager.mnemonic).thenReturn(mnemonic)

        // 1 account
        val xpub1 = "xpub1"
        val xpub2 = "xpub2"
        val btcAccount1: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val btcAccount2: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.accounts).thenReturn(mutableListOf(btcAccount1, btcAccount2))
        whenever(btcAccount1.xpubForDerivation(Derivation.LEGACY_TYPE)).thenReturn(xpub1)
        whenever(btcAccount2.xpubForDerivation(Derivation.LEGACY_TYPE)).thenReturn(xpub2)

        val metaData: GenericMetadataWallet = mock()
        val bchMetaDataAccount1: GenericMetadataAccount = mock()
        val bchMetaDataAccount2: GenericMetadataAccount = mock()
        whenever(metaData.accounts).thenReturn(
            mutableListOf(
                bchMetaDataAccount1,
                bchMetaDataAccount2
            )
        )

        // Act
        subject.restoreBchWallet(metaData)

        // Assert
        verify(bchDataStore.bchWallet)!!.addWatchOnlyAccount(xpub1)
        verify(bchDataStore.bchWallet)!!.addWatchOnlyAccount(xpub2)
        verify(metaData.accounts[0]).setXpub(xpub1)
        verify(metaData.accounts[1]).setXpub(xpub2)
    }

    @Test
    fun `restoreBchWallet no 2nd pw 1 account`() {

        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(false)
        val mnemonic = split("all all all all all all all all all all all all")
        whenever(payloadDataManager.mnemonic).thenReturn(mnemonic)

        // 1 account
        val xpub = "xpub"
        val btcAccount: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.accounts).thenReturn(mutableListOf(btcAccount))
        whenever(btcAccount.xpubForDerivation(Derivation.LEGACY_TYPE)).thenReturn(xpub)

        val metaData: GenericMetadataWallet = mock()
        val bchMetaDataAccount: GenericMetadataAccount = mock()
        whenever(metaData.accounts).thenReturn(mutableListOf(bchMetaDataAccount))

        // Act
        subject.restoreBchWallet(metaData)

        // Assert
        verify(bchDataStore.bchWallet)!!.addAccount()
        verify(metaData.accounts[0]).setXpub(xpub)
    }

    @Test
    fun `restoreBchWallet no 2nd pw 2 account`() {

        // Arrange
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(false)
        val mnemonic = split("all all all all all all all all all all all all")
        whenever(payloadDataManager.mnemonic).thenReturn(mnemonic)

        // 1 account
        val xpub1 = "xpub1"
        val xpub2 = "xpub2"
        val btcAccount1: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val btcAccount2: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        whenever(payloadDataManager.accounts).thenReturn(mutableListOf(btcAccount1, btcAccount2))
        whenever(btcAccount1.xpubForDerivation(Derivation.LEGACY_TYPE)).thenReturn(xpub1)
        whenever(btcAccount2.xpubForDerivation(Derivation.LEGACY_TYPE)).thenReturn(xpub2)

        val metaData: GenericMetadataWallet = mock()
        val bchMetaDataAccount1: GenericMetadataAccount = mock()
        val bchMetaDataAccount2: GenericMetadataAccount = mock()
        whenever(metaData.accounts).thenReturn(
            mutableListOf(
                bchMetaDataAccount1,
                bchMetaDataAccount2
            )
        )

        // Act
        subject.restoreBchWallet(metaData)

        // Assert
        verify(bchDataStore.bchWallet, times(2))!!.addAccount()
        verify(metaData.accounts[0]).setXpub(xpub1)
        verify(metaData.accounts[1]).setXpub(xpub2)
    }

    @Test
    fun `correctBtcOffsetIfNeed btc equal to bch account size`() {
        // Arrange
        val btcAccount: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val btcAccounts = mutableListOf(btcAccount)
        whenever(payloadDataManager.accounts).thenReturn(btcAccounts)

        val bchAccount: GenericMetadataAccount = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val bchAccounts = mutableListOf(bchAccount)
        whenever(bchDataStore.bchMetadata?.accounts).thenReturn(bchAccounts)

        // Act
        val needsSync = subject.correctBtcOffsetIfNeed()

        // Assert
        assertFalse(needsSync)
        verify(payloadDataManager, atLeastOnce()).accounts
        verify(bchDataStore.bchMetadata)!!.accounts
        verifyNoMoreInteractions(payloadDataManager)
        verifyNoMoreInteractions(bchDataStore.bchMetadata)
    }

    @Test
    fun `correctBtcOffsetIfNeed btc more than bch account size`() {
        // Arrange
        val btcAccount: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val btcAccounts = mutableListOf(btcAccount, btcAccount)
        whenever(payloadDataManager.accounts).thenReturn(btcAccounts)

        val bchAccount: GenericMetadataAccount = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val bchAccounts = mutableListOf(bchAccount)
        whenever(bchDataStore.bchMetadata?.accounts).thenReturn(bchAccounts)

        // Act
        val needsSync = subject.correctBtcOffsetIfNeed()

        // Assert
        assertFalse(needsSync)
        verify(payloadDataManager, atLeastOnce()).accounts
        verify(bchDataStore.bchMetadata)!!.accounts
        verifyNoMoreInteractions(payloadDataManager)
        verifyNoMoreInteractions(bchDataStore.bchMetadata)
    }

    @Test
    fun `correctBtcOffsetIfNeed btc 1 less than bch account size`() {
        // Arrange
        val btcAccountsNeeded = 1
        val mockCallCount = 1
        val xpub = XPub(address = "xpub 2", XPub.Format.LEGACY)
        val btcAccount: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS) {
            on { xpubs }.thenReturn(XPubs(listOf(xpub)))
            on { xpubForDerivation(Derivation.LEGACY_TYPE) }.thenReturn(xpub.address)
        }

        val btcAccounts = mutableListOf(btcAccount)
        whenever(payloadDataManager.accounts).thenReturn(btcAccounts)

        val bchAccount: GenericMetadataAccount = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val bchAccounts = mutableListOf(bchAccount, bchAccount)
        whenever(bchDataStore.bchMetadata?.accounts).thenReturn(bchAccounts)

        val mockWallet: Wallet = mock()
        val mockWalletBody: WalletBody = mock {
            on { addAccount(anyString()) }.thenReturn(btcAccount)
        }
        whenever(mockWallet.walletBody).thenReturn(mockWalletBody)
        whenever(payloadDataManager.wallet).thenReturn(mockWallet)

        whenever(defaultLabels.getDefaultNonCustodialWalletLabel()).thenReturn("BTC label")

        // Act
        val needsSync = subject.correctBtcOffsetIfNeed()

        // Assert
        assertTrue(needsSync)
        verify(payloadDataManager, atLeastOnce()).accounts
        verify(bchDataStore.bchMetadata, times(btcAccountsNeeded + mockCallCount))!!.accounts

        verify(payloadDataManager, times(btcAccountsNeeded)).wallet
        verify(mockWalletBody, times(btcAccountsNeeded)).addAccount("BTC label 2")
        verify(bchDataStore.bchMetadata, times(btcAccountsNeeded + mockCallCount))!!.accounts

        verifyNoMoreInteractions(payloadDataManager)
        verifyNoMoreInteractions(bchDataStore.bchMetadata)
    }

    @Test
    fun `correctBtcOffsetIfNeed btc 5 less than bch account size`() {
        // Arrange
        val btcAccountsNeeded = 5
        val mockCallCount = 1
        val xpub = XPub(address = "xpub 2", XPub.Format.LEGACY)
        val btcAccount: Account = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS) {
            on { xpubs }.thenReturn(XPubs(listOf(XPub(address = "xpub 2", XPub.Format.LEGACY))))
            on { xpubForDerivation(Derivation.LEGACY_TYPE) }.thenReturn(xpub.address)
        }
        val btcAccounts = mutableListOf(btcAccount)
        whenever(payloadDataManager.accounts).thenReturn(btcAccounts)

        val bchAccount: GenericMetadataAccount = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val bchAccounts = mutableListOf(
            bchAccount,
            bchAccount,
            bchAccount,
            bchAccount,
            bchAccount,
            bchAccount
        )
        whenever(bchDataStore.bchMetadata?.accounts).thenReturn(bchAccounts)

        val mockWallet: Wallet = mock()
        val mockWalletBody: WalletBody = mock()

        whenever(mockWalletBody.addAccount(any())).thenReturn(btcAccount)

        whenever(mockWallet.walletBody).thenReturn(mockWalletBody)
        whenever(payloadDataManager.wallet).thenReturn(mockWallet)

        // Act
        val needsSync = subject.correctBtcOffsetIfNeed()

        // Assert
        assertTrue(needsSync)
        verify(payloadDataManager, atLeastOnce()).accounts
        verify(bchDataStore.bchMetadata, times(btcAccountsNeeded + mockCallCount))!!.accounts

        verify(payloadDataManager, times(btcAccountsNeeded)).wallet
        verify(mockWalletBody, times(btcAccountsNeeded)).addAccount(any())
        verify(bchDataStore.bchMetadata, times(btcAccountsNeeded + mockCallCount))!!.accounts

        verifyNoMoreInteractions(payloadDataManager)
        verifyNoMoreInteractions(bchDataStore.bchMetadata)
    }

    @Test
    fun `get balance`() {
        val xpub = XPub("address", XPub.Format.LEGACY)
        val xpubs = XPubs(xpub)
        val map = mapOf(
            xpub.address to Balance(
                finalBalance = BigInteger.TEN,
                totalReceived = BigInteger.ZERO
            )
        )

        BchDataManager(
            payloadDataManager = mock {
                on { getBalanceOfBchAccounts(listOf(xpubs)) }.thenReturn(Observable.just(map))
            },
            bchDataStore = mock(),
            bitcoinApi = mock(),
            defaultLabels = mock(),
            metadataManager = mock(),
            crashLogger = mock(),
            kotlinSerializerFeatureFlag = mock()
        ).getBalance(xpubs)
            .test()
            .assertNoErrors()
            .assertValue(BigInteger.TEN)
    }

    @Test
    fun `get balance returns zero on error`() {
        val xpub = XPub("address", XPub.Format.LEGACY)
        val xpubs = XPubs(xpub)
        BchDataManager(
            payloadDataManager = mock {
                on { getBalanceOfBchAccounts(listOf(xpubs)) }.thenReturn(Observable.error(Exception()))
            },
            bchDataStore = mock(),
            bitcoinApi = mock(),
            defaultLabels = mock(),
            metadataManager = mock(),
            crashLogger = mock(),
            kotlinSerializerFeatureFlag = mock()
        ).getBalance(xpubs)
            .test()
            .assertNoErrors()
            .assertValue(BigInteger.ZERO)
    }

    private fun split(words: String): List<String> {
        return words.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
    }
}
