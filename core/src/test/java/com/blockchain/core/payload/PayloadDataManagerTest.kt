package com.blockchain.core.payload

import com.blockchain.api.services.NonCustodialBitcoinService
import com.blockchain.logging.RemoteLogger
import com.blockchain.testutils.rxInit
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.keys.SigningKey
import info.blockchain.wallet.payload.PayloadManager
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.ImportedAddress
import info.blockchain.wallet.payload.data.Wallet
import info.blockchain.wallet.payload.data.XPub
import info.blockchain.wallet.payload.data.XPubs
import info.blockchain.wallet.payload.model.Balance
import info.blockchain.wallet.payment.SpendableUnspentOutputs
import info.blockchain.wallet.util.PrivateKeyFactory
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.math.BigInteger
import kotlin.test.assertEquals
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.exceptions.base.MockitoException

@Suppress("IllegalIdentifier")
class PayloadDataManagerTest {

    private lateinit var subject: PayloadDataManager
    private val payloadService: PayloadService = mock()
    private val payloadManager: PayloadManager = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val privateKeyFactory: PrivateKeyFactory = mock()
    private val bitcoinApi: NonCustodialBitcoinService = mock()

    private val remoteLogger: RemoteLogger = mock()
    private val testScheduler = TestScheduler()

    @Suppress("unused")
    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computation(testScheduler)
    }

    @Before
    fun setUp() {
        subject = PayloadDataManager(
            payloadService,
            bitcoinApi,
            privateKeyFactory,
            payloadManager,
            remoteLogger
        )
    }

    @Test
    fun initializeFromPayload() {
        // Arrange
        val payload = "{}"
        val password = "PASSWORD"
        whenever(payloadService.initializeFromPayload(payload, password))
            .thenReturn(Completable.complete())
        // Act
        val testObserver = subject.initializeFromPayload(payload, password).test()
        // Assert
        verify(payloadService).initializeFromPayload(payload, password)
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
    }

    @Test
    fun restoreHdWallet() {
        // Arrange
        val mnemonic = "MNEMONIC"
        val walletName = "WALLET_NAME"
        val email = "EMAIL"
        val password = "PASSWORD"
        val mockWallet: Wallet = mock()
        whenever(payloadService.restoreHdWallet(mnemonic, walletName, email, password))
            .thenReturn(Single.just(mockWallet))

        // Act
        val testObserver = subject.restoreHdWallet(mnemonic, walletName, email, password).test()

        // Assert
        verify(payloadService).restoreHdWallet(mnemonic, walletName, email, password)
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
        testObserver.assertValue(mockWallet)
    }

    @Test
    fun createHdWallet() {
        // Arrange
        val password = "PASSWORD"
        val email = "EMAIL"
        val walletName = "WALLET_NAME"
        val recaptcha = "CAPTCHA"
        val mockWallet: Wallet = mock()
        whenever(payloadService.createHdWallet(password, walletName, email, recaptcha))
            .thenReturn(Single.just(mockWallet))

        // Act
        subject.createHdWallet(password, walletName, email, recaptcha)
            .test()
            .assertComplete()
            .assertValue(mockWallet)

        // Assert
        verify(payloadService).createHdWallet(password, walletName, email, recaptcha)
        verifyNoMoreInteractions(payloadService)
    }

    @Test
    fun initializeAndDecrypt() {
        // Arrange
        val sharedKey = "SHARED_KEY"
        val guid = "GUID"
        val password = "PASSWORD"
        whenever(payloadService.initializeAndDecrypt(sharedKey, guid, password))
            .thenReturn(Completable.complete())

        // Act
        subject.initializeAndDecrypt(sharedKey, guid, password)
            .test()
            .assertComplete()

        // Assert
        verify(payloadService).initializeAndDecrypt(sharedKey, guid, password)
        verifyNoMoreInteractions(payloadService)
    }

    @Test
    fun handleQrCode() {
        // Arrange
        val data = "DATA"
        whenever(payloadService.handleQrCode(data)).thenReturn(Completable.complete())

        // Act
        subject.handleQrCode(data).test()
            .assertComplete()

        // Assert
        verify(payloadService).handleQrCode(data)
        verifyNoMoreInteractions(payloadService)
    }

    @Test
    fun upgradeV2toV3() {
        // Arrange
        val secondPassword = "SECOND_PASSWORD"
        val defaultAccountName = "DEFAULT_ACCOUNT_NAME"

        whenever(payloadManager.isV3UpgradeRequired()).thenReturn(true)
        whenever(payloadManager.isV4UpgradeRequired()).thenReturn(false)

        // Act
        subject.upgradeWalletPayload(secondPassword, defaultAccountName)
            .test()
            .assertComplete()

        // Assert
        verify(payloadManager).upgradeV2PayloadToV3(secondPassword, defaultAccountName)
        verify(payloadManager).isV3UpgradeRequired()
        verify(payloadManager, never()).isV4UpgradeRequired()
        verify(payloadManager).payload

        verifyNoMoreInteractions(payloadService)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun upgradeV2toV4() {
        // Arrange
        val secondPassword = "SECOND_PASSWORD"
        val defaultAccountName = "DEFAULT_ACCOUNT_NAME"

        whenever(payloadManager.isV3UpgradeRequired()).thenReturn(true)
        whenever(payloadManager.isV4UpgradeRequired()).thenReturn(true)
        whenever(payloadManager.upgradeV2PayloadToV3(any(), any())).thenReturn(Completable.complete())
        whenever(payloadManager.upgradeV3PayloadToV4(any())).thenReturn(Completable.complete())

        // Act
        subject.upgradeWalletPayload(secondPassword, defaultAccountName)
            .test()
            .assertComplete()

        // Assert
        verify(payloadManager).upgradeV2PayloadToV3(secondPassword, defaultAccountName)
        verify(payloadManager).upgradeV3PayloadToV4(secondPassword)
        verify(payloadManager).isV3UpgradeRequired()
        verify(payloadManager).payload

        verifyNoMoreInteractions(payloadService)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun upgradeV3to4() {
        // Arrange
        val secondPassword = "SECOND_PASSWORD"
        val defaultAccountName = "DEFAULT_ACCOUNT_NAME"

        whenever(payloadManager.isV3UpgradeRequired()).thenReturn(false)
        whenever(payloadManager.isV4UpgradeRequired()).thenReturn(true)

        // Act
        subject.upgradeWalletPayload(secondPassword, defaultAccountName)
            .test()
            .assertComplete()

        // Assert
        verify(payloadManager).upgradeV3PayloadToV4(secondPassword)
        verify(payloadManager).isV3UpgradeRequired()
        verify(payloadManager).isV4UpgradeRequired()
        verify(payloadManager).payload

        verifyNoMoreInteractions(payloadService)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun upgradeV2toV4_v3_fails() {
        // Arrange
        val secondPassword = "SECOND_PASSWORD"
        val defaultAccountName = "DEFAULT_ACCOUNT_NAME"
        whenever(payloadManager.upgradeV2PayloadToV3(secondPassword, defaultAccountName))
            .thenReturn(Completable.error(MockitoException("Failed")))
        whenever(payloadManager.isV3UpgradeRequired()).thenReturn(true)
        whenever(payloadManager.isV4UpgradeRequired()).thenReturn(true)

        // Act
        subject.upgradeWalletPayload(secondPassword, defaultAccountName)
            .test()
            .assertError(Throwable::class.java)

        // Assert
        verify(payloadManager).upgradeV2PayloadToV3(secondPassword, defaultAccountName)
        verify(payloadManager).isV3UpgradeRequired()
        verify(payloadManager).payload

        verifyNoMoreInteractions(payloadService)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun upgradeV2toV4_v4_fails() {
        // Arrange
        val secondPassword = "SECOND_PASSWORD"
        val defaultAccountName = "DEFAULT_ACCOUNT_NAME"
        whenever(payloadManager.upgradeV3PayloadToV4(secondPassword)).thenReturn(
            Completable.error(MockitoException("Failed"))
        )

        whenever(payloadManager.upgradeV2PayloadToV3(secondPassword, defaultAccountName)).thenReturn(
            Completable.complete()
        )

        whenever(payloadManager.isV3UpgradeRequired()).thenReturn(true)
        whenever(payloadManager.isV4UpgradeRequired()).thenReturn(true)

        // Act
        subject.upgradeWalletPayload(secondPassword, defaultAccountName)
            .test()
            .assertError(Throwable::class.java)

        // Assert
        verify(payloadManager).upgradeV2PayloadToV3(secondPassword, defaultAccountName)
        verify(payloadManager).upgradeV3PayloadToV4(secondPassword)
        verify(payloadManager).isV3UpgradeRequired()
        verify(payloadManager, never()).isV4UpgradeRequired()
        verify(payloadManager).payload

        verifyNoMoreInteractions(payloadService)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun syncPayloadAndPublicKeys() {
        // Arrange
        whenever(payloadService.syncPayloadAndPublicKeys()).thenReturn(Completable.complete())
        // Act
        val testObserver = subject.syncPayloadAndPublicKeys().test()
        // Assert
        verify(payloadService).syncPayloadAndPublicKeys()
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
    }

    @Test
    fun updateAllTransactions() {
        // Arrange
        whenever(payloadService.updateAllTransactions()).thenReturn(Completable.complete())
        // Act
        val testObserver = subject.updateAllTransactions().test()
        // Assert
        verify(payloadService).updateAllTransactions()
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
    }

    @Test
    fun updateAllBalances() {
        // Arrange
        whenever(payloadService.updateAllBalances()).thenReturn(Completable.complete())
        // Act
        val testObserver = subject.updateAllBalances().test()
        // Assert
        verify(payloadService).updateAllBalances()
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
    }

    @Test
    fun updateTransactionNotes() {
        // Arrange
        val txHash = "TX_HASH"
        val note = "note"
        whenever(payloadService.updateTransactionNotes(txHash, note))
            .thenReturn(Completable.complete())
        // Act
        val testObserver = subject.updateTransactionNotes(txHash, note).test()
        // Assert
        verify(payloadService).updateTransactionNotes(txHash, note)
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
    }

    @Test
    fun getBalanceOfBchAddresses() {
        // Arrange
        val xpub = XPub("ADDRESS", XPub.Format.LEGACY)
        val xpubs = XPubs(xpub)

        val hashMap = mapOf(Pair(xpub.address, mock<Balance>()))
        whenever(payloadService.getBalanceOfBchAccounts(listOf(xpubs)))
            .thenReturn(Observable.just(hashMap))

        // Act
        val testObserver = subject.getBalanceOfBchAccounts(listOf(xpubs)).test()

        // Assert
        verify(payloadService).getBalanceOfBchAccounts(listOf(xpubs))
        verifyNoMoreInteractions(payloadService)
        testObserver.assertComplete()
        testObserver.assertValue(hashMap)
    }

    @Test
    fun addressToLabel() {
        // Arrange
        val address = "ADDRESS"
        val label = "label"
        whenever(payloadManager.getLabelFromAddress(address)).thenReturn(label)
        // Act
        val result = subject.addressToLabel(address)
        // Assert
        verify(payloadManager).getLabelFromAddress(address)
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo label
    }

    @Test
    fun `getNextReceiveAddress based on account index`() {
        // Arrange
        val index = 0
        val mockAccount: Account = mock()
        val accounts = mutableListOf(mockAccount)
        val address = "ADDRESS"
        whenever(payloadManager.payload?.walletBody?.accounts).thenReturn(accounts)
        whenever(payloadManager.getNextReceiveAddress(mockAccount)).thenReturn(address)
        // Act
        val testObserver = subject.getNextReceiveAddress(index).test()
        testScheduler.triggerActions()
        // Assert
        verify(payloadManager).getNextReceiveAddress(mockAccount)
        testObserver.assertComplete()
        testObserver.assertValue(address)
    }

    @Test
    fun `getNextReceiveAddress from account`() {
        // Arrange
        val mockAccount: Account = mock()
        val address = "ADDRESS"
        whenever(payloadManager.getNextReceiveAddress(mockAccount)).thenReturn(address)
        // Act
        val testObserver = subject.getNextReceiveAddress(mockAccount).test()
        testScheduler.triggerActions()
        // Assert
        verify(payloadManager).getNextReceiveAddress(mockAccount)
        testObserver.assertComplete()
        testObserver.assertValue(address)
    }

    @Test
    fun getNextReceiveAddressAndReserve() {
        // Arrange
        val accountIndex = 0
        val addressLabel = "ADDRESS_LABEL"
        val address = "ADDRESS"
        val mockAccount: Account = mock()
        val accounts = mutableListOf(mockAccount)
        whenever(payloadManager.payload?.walletBody?.accounts).thenReturn(accounts)
        whenever(
            payloadManager.getNextReceiveAddressAndReserve(
                mockAccount,
                addressLabel
            )
        ).thenReturn(Single.just(address))

        // Act
        val testObserver = subject.getNextReceiveAddressAndReserve(accountIndex, addressLabel).test()
        testScheduler.triggerActions()

        // Assert
        verify(payloadManager).getNextReceiveAddressAndReserve(mockAccount, addressLabel)
        testObserver.assertComplete()
        testObserver.assertValue(address)
    }

    @Test
    fun `getNextChangeAddress based on account index`() {
        // Arrange
        val index = 0
        val mockAccount: Account = mock()
        val accounts = mutableListOf(mockAccount)
        val address = "ADDRESS"

        whenever(payloadManager.payload?.walletBody?.accounts).thenReturn(accounts)
        whenever(payloadManager.getNextChangeAddress(mockAccount)).thenReturn(address)

        // Act
        val testObserver = subject.getNextChangeAddress(index).test()
        testScheduler.triggerActions()

        // Assert
        verify(payloadManager).getNextChangeAddress(mockAccount)
        testObserver.assertComplete()
        testObserver.assertValue(address)
    }

    @Test
    fun `getNextChangeAddress from account`() {
        // Arrange
        val mockAccount: Account = mock()
        val address = "ADDRESS"
        whenever(payloadManager.getNextChangeAddress(mockAccount)).thenReturn(address)
        // Act
        val testObserver = subject.getNextChangeAddress(mockAccount).test()
        testScheduler.triggerActions()
        // Assert
        verify(payloadManager).getNextChangeAddress(mockAccount)
        testObserver.assertComplete()
        testObserver.assertValue(address)
    }

    @Test
    fun getAddressECKey() {
        // Arrange
        val mockImportedAddress: ImportedAddress = mock()
        val secondPassword = "SECOND_PASSWORD"
        val mockKey: SigningKey = mock()
        whenever(payloadManager.getAddressSigningKey(mockImportedAddress, secondPassword))
            .thenReturn(mockKey)
        // Act
        val result = subject.getAddressSigningKey(mockImportedAddress, secondPassword)
        // Assert
        verify(payloadManager).getAddressSigningKey(mockImportedAddress, secondPassword)
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo mockKey
    }

    @Test
    fun createNewAccount() {
        // Arrange
        val mockAccount: Account = mock()
        whenever(payloadService.createNewAccount(anyString(), isNull()))
            .thenReturn(Observable.just(mockAccount))
        // Act
        val observer = subject.createNewAccount("", null).test()
        // Assert
        verify(payloadService).createNewAccount("", null)
        observer.assertNoErrors()
        observer.assertComplete()
        assertEquals(mockAccount, observer.values()[0])
    }

    @Test
    fun getKeyFromImportedData() {
        // Arrange
        val data = "DATA"
        val mockKey: SigningKey = mock()
        whenever(privateKeyFactory.getKeyFromImportedData(PrivateKeyFactory.BASE58, data, bitcoinApi))
            .thenReturn(mockKey)
        // Act
        val testObserver = subject.getKeyFromImportedData(PrivateKeyFactory.BASE58, data).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(mockKey)
    }

    @Test
    fun `getAccounts returns list of accounts`() {
        // Arrange
        val mockAccount: Account = mock()
        val accounts = mutableListOf(mockAccount)
        whenever(payloadManager.payload?.walletBody?.accounts)
            .thenReturn(accounts)
        // Act
        val result = subject.accounts
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result shouldBeEqualTo accounts
    }

    @Test
    fun `getImportedAddresses returns list of imported addresses`() {
        // Arrange
        val mockImportedAddress: ImportedAddress = mock {
            on { privateKey }.thenReturn("SomeRandomKeyString")
        }
        val addresses = listOf(mockImportedAddress)
        whenever(payloadManager.payload?.importedAddressList).thenReturn(addresses)
        // Act
        val result = subject.importedAddresses
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result shouldBeEqualTo addresses
    }

    @Test
    fun `getImportedAddresses returns list of imported addresses with filters out watch only`() {
        // Arrange
        val mockImportedAddress1: ImportedAddress = mock {
            on { privateKey }.thenReturn(null)
        }
        val mockImportedAddress2: ImportedAddress = mock {
            on { privateKey }.thenReturn("SomeRandomKeyString")
        }
        val addresses = listOf(mockImportedAddress1, mockImportedAddress2)
        whenever(payloadManager.payload?.importedAddressList).thenReturn(addresses)
        // Act
        val result = subject.importedAddresses
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result.count() shouldBeEqualTo 1
        result[0] shouldBeEqualTo mockImportedAddress2
    }

    @Test
    fun getAddressBalance() {
        // Arrange
        val address = "ADDRESS"
        val xpub = XPub(address = address, derivation = XPub.Format.LEGACY)
        val xpubs = XPubs(xpub)

        val balance = CryptoValue.fromMinor(CryptoCurrency.BTC, BigInteger.TEN)
        whenever(payloadManager.getAddressBalance(xpubs))
            .thenReturn(balance)

        // Act
        val result = subject.getAddressBalance(xpubs)

        // Assert
        verify(payloadManager).getAddressBalance(xpubs)
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo balance
    }

    @Test
    fun getReceiveAddressAtPosition() {
        // Arrange
        val mockAccount: Account = mock()
        val position = 1337
        val address = "ADDRESS"
        whenever(payloadManager.getReceiveAddressAtPosition(mockAccount, position))
            .thenReturn(address)
        // Act
        val result = subject.getReceiveAddressAtPosition(mockAccount, position)
        // Assert
        verify(payloadManager).getReceiveAddressAtPosition(mockAccount, position)
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo address
    }

    @Test
    fun subtractAmountFromAddressBalance() {
        // Arrange
        val address = "ADDRESS"
        val amount = 1_000_000L
        // Act
        subject.subtractAmountFromAddressBalance(address, amount)
        // Assert
        verify(payloadManager).subtractAmountFromAddressBalance(address, BigInteger.valueOf(amount))
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun incrementReceiveAddress() {
        // Arrange
        val mockAccount: Account = mock()
        // Act
        subject.incrementReceiveAddress(mockAccount)
        // Assert
        verify(payloadManager).incrementNextReceiveAddress(mockAccount)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun incrementChangeAddress() {
        // Arrange
        val mockAccount: Account = mock()
        // Act
        subject.incrementChangeAddress(mockAccount)
        // Assert
        verify(payloadManager).incrementNextChangeAddress(mockAccount)
        verifyNoMoreInteractions(payloadManager)
    }

    @Test
    fun getXpubFromAddress() {
        // Arrange
        val xPub = "X_PUB"
        val address = "ADDRESS"
        whenever(payloadManager.getXpubFromAddress(address))
            .thenReturn(xPub)
        // Act
        val result = subject.getXpubFromAddress(address)
        // Assert
        verify(payloadManager).getXpubFromAddress(address)
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo xPub
    }

    @Test
    fun isOwnHDAddress() {
        // Arrange
        val address = "ADDRESS"
        whenever(payloadManager.isOwnHDAddress(address)).thenReturn(true)
        // Act
        val result = subject.isOwnHDAddress(address)
        // Assert
        result shouldBeEqualTo true
    }

    @Test
    fun `getWallet returns wallet`() {
        // Arrange
        val mockWallet: Wallet = mock()
        whenever(payloadManager.payload).thenReturn(mockWallet)
        // Act
        val result = subject.wallet
        // Assert
        verify(payloadManager).payload
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo mockWallet
    }

    @Test
    fun `getWallet returns null`() {
        // Arrange
        whenever(payloadManager.payload).thenReturn(null)
        // Act
        val result = subject.wallet
        // Assert
        verify(payloadManager).payload
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo null
    }

    @Test
    fun getDefaultAccountIndex() {
        // Arrange
        val index = 42
        whenever(payloadManager.payload?.walletBody?.defaultAccountIdx).thenReturn(index)
        // Act
        val result = subject.defaultAccountIndex
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result shouldBeEqualTo index
    }

    @Test
    fun getDefaultAccount() {
        // Arrange
        val index = 42
        val mockAccount: Account = mock()
        whenever(payloadManager.payload?.walletBody?.defaultAccountIdx)
            .thenReturn(index)
        whenever(payloadManager.payload?.walletBody?.getAccount(index))
            .thenReturn(mockAccount)
        // Act
        val result = subject.defaultAccount
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result shouldBeEqualTo mockAccount
    }

    @Test
    fun getAccount() {
        // Arrange
        val index = 42
        val mockAccount: Account = mock()
        whenever(payloadManager.payload?.walletBody?.getAccount(index))
            .thenReturn(mockAccount)
        // Act
        val result = subject.getAccount(index)
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result shouldBeEqualTo mockAccount
    }

    @Test
    fun getTransactionNotes() {
        // Arrange
        val txHash = "TX_HASH"
        val note = "NOTES"
        val map = mutableMapOf(txHash to note)
        whenever(payloadManager.payload?.txNotes).thenReturn(map)
        // Act
        val result = subject.getTransactionNotes(txHash)
        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result `should be equal to` note
    }

    @Test
    fun getHDKeysForSigning() {
        // Arrange
        val mockAccount: Account = mock()
        val mockOutputs: SpendableUnspentOutputs = mock()
        val mockSigningKey: SigningKey = mock()
        whenever(
            payloadManager.payload?.walletBody?.getHDKeysForSigning(
                mockAccount,
                mockOutputs
            )
        ).thenReturn(listOf(mockSigningKey))

        // Act
        val result = subject.getHDKeysForSigning(mockAccount, mockOutputs)

        // Assert
        verify(payloadManager, atLeastOnce()).payload
        result shouldBeEqualTo listOf(mockSigningKey)
    }

    @Test
    fun getPayloadChecksum() {
        // Arrange
        val checkSum = "CHECKSUM"
        whenever(payloadManager.payloadChecksum).thenReturn(checkSum)
        // Act
        val result = subject.payloadChecksum
        // Assert
        verify(payloadManager).payloadChecksum
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo checkSum
    }

    @Test
    fun getTempPassword() {
        // Arrange
        val tempPassword = "TEMP_PASSWORD"
        whenever(payloadManager.tempPassword).thenReturn(tempPassword)
        // Act
        val result = subject.tempPassword
        // Assert
        verify(payloadManager).tempPassword
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo tempPassword
    }

    @Test
    fun updatePasswordSuccess() {
        // Arrange
        val tempPassword = "TEMP_PASSWORD"
        whenever(payloadManager.updatePassword(tempPassword)).thenReturn(Completable.complete())
        // Act
        val test = subject.updatePassword(tempPassword).test()
        // Assert

        test.assertComplete()
    }

    @Test
    fun updatePasswordFailed() {
        // Arrange
        val tempPassword = "TEMP_PASSWORD"
        whenever(payloadManager.updatePassword(tempPassword)).thenReturn(Completable.error(RuntimeException()))
        // Act
        val test = subject.updatePassword(tempPassword).test()
        // Assert

        test.assertError(RuntimeException::class.java)
    }

    @Test
    fun getImportedAddressesBalance() {
        // Arrange
        val balance = BigInteger.TEN
        whenever(payloadManager.importedAddressesBalance).thenReturn(balance)
        // Act
        val result = subject.importedAddressesBalance
        // Assert
        verify(payloadManager).importedAddressesBalance
        verifyNoMoreInteractions(payloadManager)
        result shouldBeEqualTo balance
    }

    @Test
    fun isDoubleEncrypted() {
        // Arrange
        whenever(payloadManager.payload.isDoubleEncryption).thenReturn(true)
        // Act
        val result = subject.isDoubleEncrypted
        // Assert
        result shouldBeEqualTo true
    }
}
