package piuk.blockchain.android.ui.backup.wordlist

import android.os.Bundle
import com.blockchain.wallet.BackupWallet
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.amshove.kluent.`should be equal to`
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.backup.wordlist.BackupWalletWordListFragment.Companion.ARGUMENT_SECOND_PASSWORD

class BackupWalletWordListPresenterTest {

    private lateinit var subject: BackupWalletWordListPresenter
    private val backupWallet: BackupWallet = mock()
    private val view: BackupWalletWordListView = mock()

    @Before
    fun setUp() {
        subject = BackupWalletWordListPresenter(backupWallet)
        subject.initView(view)
    }

    @Test
    fun `onViewReady no mnemonic`() {
        // Arrange
        val password = "PASSWORD"
        val bundle: Bundle = mock()
        whenever(bundle.getString(ARGUMENT_SECOND_PASSWORD)).thenReturn(password)
        whenever(view.getPageBundle()).thenReturn(bundle)
        whenever(backupWallet.getMnemonic(password)).thenReturn(null)
        // Act
        subject.onViewReady()
        // Assert
        verify(view).getPageBundle()
        verify(view).finish()
        verifyNoMoreInteractions(view)
        verify(backupWallet).getMnemonic(password)
        verifyNoMoreInteractions(backupWallet)
    }

    @Test
    fun `onViewReady mnemonic loaded`() {
        // Arrange
        val password = "PASSWORD"
        val bundle: Bundle = mock()
        whenever(bundle.getString(ARGUMENT_SECOND_PASSWORD)).thenReturn(password)
        val mnemonic = listOf("one", "two", "three", "four")
        whenever(view.getPageBundle()).thenReturn(bundle)
        whenever(backupWallet.getMnemonic(password)).thenReturn(mnemonic)
        // Act
        subject.onViewReady()
        // Assert
        verify(view).getPageBundle()
        verifyNoMoreInteractions(view)
        verify(backupWallet).getMnemonic(password)
        verifyNoMoreInteractions(backupWallet)
    }

    @Test
    fun getWordForIndex() {
        // Arrange
        val password = "PASSWORD"
        val bundle: Bundle = mock()
        whenever(bundle.getString(ARGUMENT_SECOND_PASSWORD)).thenReturn(password)
        val mnemonic = listOf("one", "two", "three", "four")
        whenever(view.getPageBundle()).thenReturn(bundle)
        whenever(backupWallet.getMnemonic(password)).thenReturn(mnemonic)
        // Act
        subject.onViewReady()
        val result = subject.getWordForIndex(2)!!
        // Assert
        verify(view).getPageBundle()
        verifyNoMoreInteractions(view)
        verify(backupWallet).getMnemonic(password)
        verifyNoMoreInteractions(backupWallet)
        result `should be equal to` "three"
    }

    @Test
    fun getMnemonicSize() {
        // Arrange
        val password = "PASSWORD"
        val bundle: Bundle = mock()
        whenever(bundle.getString(ARGUMENT_SECOND_PASSWORD)).thenReturn(password)
        val mnemonic = listOf("one", "two", "three", "four")
        whenever(view.getPageBundle()).thenReturn(bundle)
        whenever(backupWallet.getMnemonic(password)).thenReturn(mnemonic)
        // Act
        subject.onViewReady()
        val result = subject.getMnemonicSize()
        // Assert
        verify(view).getPageBundle()
        verifyNoMoreInteractions(view)
        verify(backupWallet).getMnemonic(password)
        verifyNoMoreInteractions(backupWallet)
        result `should be equal to` 4
    }
}
