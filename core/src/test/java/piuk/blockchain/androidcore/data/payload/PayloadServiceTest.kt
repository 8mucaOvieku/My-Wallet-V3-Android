package piuk.blockchain.androidcore.data.payload

import com.blockchain.android.testutils.rxInit
import com.blockchain.api.ApiException
import com.blockchain.remoteconfig.IntegratedFeatureFlag
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.wallet.keys.SigningKey
import info.blockchain.wallet.payload.PayloadManager
import info.blockchain.wallet.payload.data.Account
import info.blockchain.wallet.payload.data.ImportedAddress
import info.blockchain.wallet.payload.data.Wallet
import info.blockchain.wallet.payload.data.XPub
import info.blockchain.wallet.payload.data.XPubs
import info.blockchain.wallet.payload.model.Balance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS

@Suppress("IllegalIdentifier")
class PayloadServiceTest {

    private lateinit var subject: PayloadService
    private val mockPayloadManager: PayloadManager = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val kotlinSerializerFeatureFlag: IntegratedFeatureFlag = mock()

    @Suppress("unused")
    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = PayloadService(
            payloadManager = mockPayloadManager,
            kotlinSerializerFeatureFlag = kotlinSerializerFeatureFlag
        )
    }

    @Test
    fun initializeFromPayload() {
        // Arrange
        val payload = "PAYLOAD"
        val password = "PASSWORD"
        val withKotlinX = true

        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)

        // Act
        val testObserver =
            subject.initializeFromPayload(payload, password).test()
        // Assert
        verify(mockPayloadManager).initializeAndDecryptFromPayload(
            payload,
            password,
            withKotlinX
        )
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun restoreHdWallet_v4() {
        // Arrange
        val mnemonic = "MNEMONIC"
        val walletName = "WALLET_NAME"
        val email = "EMAIL"
        val password = "PASSWORD"
        val mockWallet: Wallet = mock()
        val v4Enabled = true
        val withKotlinX = true
        whenever(mockPayloadManager.recoverFromMnemonic(mnemonic, walletName, email, password, v4Enabled, withKotlinX))
            .thenReturn(mockWallet)
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)

        // Act
        val testObserver = subject.restoreHdWallet(mnemonic, walletName, email, password).test()

        // Assert
        verify(mockPayloadManager).recoverFromMnemonic(mnemonic, walletName, email, password, v4Enabled, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
        testObserver.assertValue(mockWallet)
    }

    @Test
    fun createHdWallet_v4() {
        // Arrange
        val password = "PASSWORD"
        val walletName = "WALLET_NAME"
        val email = "EMAIL"
        val mockWallet: Wallet = mock()
        val v4Enabled = true
        val withKotlinX = true
        whenever(mockPayloadManager.create(walletName, email, password, v4Enabled, withKotlinX)).thenReturn(mockWallet)
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        // Act
        val testObserver = subject.createHdWallet(password, walletName, email).test()
        // Assert
        verify(mockPayloadManager).create(walletName, email, password, v4Enabled, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
        testObserver.assertValue(mockWallet)
    }

    @Test
    fun initializeAndDecrypt_v4() {
        // Arrange
        val sharedKey = "SHARED_KEY"
        val guid = "GUID"
        val password = "PASSWORD"
        val v4Enabled = true
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)

        // Act
        val testObserver = subject.initializeAndDecrypt(sharedKey, guid, password)
            .test()

        // Assert
        verify(mockPayloadManager).initializeAndDecrypt(
            sharedKey,
            guid,
            password,
            v4Enabled,
            withKotlinX
        )
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun handleQrCode_v4() {
        // Arrange
        val qrString = "QR_STRING"
        val v4Enabled = true
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)

        // Act
        val testObserver = subject.handleQrCode(qrString).test()

        // Assert
        verify(mockPayloadManager).initializeAndDecryptFromQR(qrString, v4Enabled, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun `syncPayloadWithServer successful`() {
        // Arrange
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(mockPayloadManager.save(withKotlinX)).thenReturn(true)
        // Act
        val testObserver = subject.syncPayloadWithServer().test()
        // Assert
        verify(mockPayloadManager).save(withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun `syncPayloadWithServer failed`() {
        // Arrange
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(mockPayloadManager.save(withKotlinX)).thenReturn(false)
        // Act
        val testObserver = subject.syncPayloadWithServer().test()
        // Assert
        verify(mockPayloadManager).save(withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertNotComplete()
        testObserver.assertError(ApiException::class.java)
    }

    @Test
    fun `syncPayloadAndPublicKeys successful`() {
        // Arrange
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(mockPayloadManager.saveAndSyncPubKeys(withKotlinX)).thenReturn(true)
        // Act
        val testObserver = subject.syncPayloadAndPublicKeys().test()
        // Assert
        verify(mockPayloadManager).saveAndSyncPubKeys(withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun `syncPayloadAndPublicKeys failed`() {
        // Arrange
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(mockPayloadManager.saveAndSyncPubKeys(withKotlinX)).thenReturn(false)
        // Act
        val testObserver = subject.syncPayloadAndPublicKeys().test()
        // Assert
        verify(mockPayloadManager).saveAndSyncPubKeys(withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertNotComplete()
        testObserver.assertError(ApiException::class.java)
    }

    @Test
    fun updateAllTransactions() {
        // Arrange

        // Act
        val testObserver = subject.updateAllTransactions().test()
        // Assert
        verify(mockPayloadManager).getAllTransactions(50, 0)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun updateAllBalances() {
        // Arrange

        // Act
        val testObserver = subject.updateAllBalances().test()
        // Assert
        verify(mockPayloadManager).updateAllBalances()
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun getBalanceOfBchAddresses() {
        // Arrange
        val addresses = listOf("address_one", "address_two", "address_three")
        val map = mapOf(
            Pair("address_one", mock()),
            Pair("address_two", mock()),
            Pair("address_three", mock<Balance>())
        )

        val xpubs = addresses.map { XPubs(XPub(it, XPub.Format.LEGACY)) }
        whenever(mockPayloadManager.getBalanceOfBchAccounts(xpubs))
            .thenReturn(map)
        // Act
        val testObserver = subject.getBalanceOfBchAccounts(xpubs).test()
        // Assert
        verify(mockPayloadManager).getBalanceOfBchAccounts(xpubs)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
        testObserver.assertValue(map)
    }

    @Test
    fun updateTransactionNotes() {
        // Arrange
        val txHash = "TX_HASH"
        val note = "NOTE"
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(mockPayloadManager.payload?.txNotes).thenReturn(mutableMapOf())
        whenever(mockPayloadManager.save(withKotlinX)).thenReturn(true)
        // Act
        val testObserver = subject.updateTransactionNotes(txHash, note).test()
        // Assert
        verify(mockPayloadManager, atLeastOnce()).payload
        verify(mockPayloadManager).save(withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun createNewAccount() {
        // Arrange
        val label = "LABEL"
        val secondPassword = "SECOND_PASSWORD"
        val mockAccount: Account = mock()
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(
            mockPayloadManager.addAccount(
                label,
                secondPassword,
                withKotlinX
            )
        ).thenReturn(mockAccount)
        // Act
        val testObserver = subject.createNewAccount(label, secondPassword).test()
        // Assert
        verify(mockPayloadManager).addAccount(label, secondPassword, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
        testObserver.assertValue(mockAccount)
    }

    @Test
    fun setKeyForImportedAddress() {
        // Arrange
        val mockKey: SigningKey = mock()
        val secondPassword = "SECOND_PASSWORD"
        val mockImportedAddress: ImportedAddress = mock()
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        whenever(mockPayloadManager.setKeyForImportedAddress(mockKey, secondPassword, withKotlinX))
            .thenReturn(mockImportedAddress)
        // Act
        val testObserver = subject.setKeyForImportedAddress(mockKey, secondPassword).test()
        // Assert
        verify(mockPayloadManager).setKeyForImportedAddress(mockKey, secondPassword, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
        testObserver.assertValue(mockImportedAddress)
    }

    @Test
    fun addImportedAddress() {
        // Arrange
        val mockImportedAddress: ImportedAddress = mock()
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        // Act
        val testObserver = subject.addImportedAddress(mockImportedAddress).test()
        // Assert
        verify(mockPayloadManager).addImportedAddress(mockImportedAddress, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }

    @Test
    fun updateImportedAddress() {
        // Arrange
        val mockImportedAddress: ImportedAddress = mock()
        val withKotlinX = true
        whenever(kotlinSerializerFeatureFlag.isEnabled).thenReturn(withKotlinX)
        // Act
        val testObserver = subject.updateImportedAddress(mockImportedAddress).test()
        // Assert
        verify(mockPayloadManager).updateImportedAddress(mockImportedAddress, withKotlinX)
        verifyNoMoreInteractions(mockPayloadManager)
        testObserver.assertComplete()
    }
}
