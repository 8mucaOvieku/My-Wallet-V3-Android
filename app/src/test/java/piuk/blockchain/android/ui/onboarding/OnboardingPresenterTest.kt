package piuk.blockchain.android.ui.onboarding

import com.blockchain.core.access.PinRepository
import com.blockchain.core.settings.SettingsDataManager
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.wallet.api.data.Settings
import io.reactivex.rxjava3.core.Observable
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.data.biometrics.BiometricsController

class OnboardingPresenterTest {

    private lateinit var subject: OnboardingPresenter
    private val mockBiometricsController: BiometricsController = mock()
    private val mockPinRepository: PinRepository = mock()
    private val mockSettingsDataManager: SettingsDataManager = mock()
    private val view: OnboardingView = mock()

    @Before
    fun setUp() {
        subject = OnboardingPresenter(
            mockBiometricsController,
            mockPinRepository,
            mockSettingsDataManager
        )
        subject.initView(view)
    }

    @Test
    fun onViewReadySettingsFailureEmailOnly() {
        // Arrange
        whenever(view.showEmail).thenReturn(true)
        whenever(mockSettingsDataManager.getSettings()).thenReturn(Observable.error { Throwable() })
        // Act
        subject.onViewReady()
        // Assert
        verify(mockSettingsDataManager).getSettings()
        verifyNoMoreInteractions(mockSettingsDataManager)
        verify(view).showEmailPrompt()
        verify(view).showEmail
        verifyNoMoreInteractions(view)
    }

    @Test
    fun onViewReadyFingerprintHardwareAvailable() {
        // Arrange
        val mockSettings: Settings = mock()
        whenever(mockSettingsDataManager.getSettings()).thenReturn(Observable.just(mockSettings))
        whenever(mockBiometricsController.isHardwareDetected).thenReturn(true)
        whenever(view.showEmail).thenReturn(false)
        whenever(view.showFingerprints).thenReturn(true)

        // Act
        subject.onViewReady()
        // Assert
        verify(mockSettingsDataManager).getSettings()
        verifyNoMoreInteractions(mockSettingsDataManager)
        verifyNoMoreInteractions(mockBiometricsController)
        verify(view).showFingerprintPrompt()
        verify(view).showEmail
        verify(view).showFingerprints

        verifyNoMoreInteractions(view)
    }

    @Test
    fun onEnableFingerprintClickedFingerprintEnrolled() {
        // Arrange
        val captor = argumentCaptor<String>()
        val pin = "1234"
        whenever(mockBiometricsController.isBiometricAuthEnabled).thenReturn(true)
        whenever(mockPinRepository.pin).thenReturn(pin)
        // Act
        subject.onEnableFingerprintClicked()
        // Assert
        verify(mockBiometricsController).isBiometricAuthEnabled
        verifyNoMoreInteractions(mockBiometricsController)
        verify(mockPinRepository).pin
        verifyNoMoreInteractions(mockPinRepository)
        verify(view).showFingerprintDialog(captor.capture())
        verifyNoMoreInteractions(view)
        captor.firstValue shouldEqual pin
    }

    @Test(expected = IllegalStateException::class)
    fun onEnableFingerprintClickedNoPinFound() {
        // Arrange
        whenever(mockBiometricsController.isBiometricAuthEnabled).thenReturn(true)
        whenever(mockPinRepository.pin).thenReturn("")
        // Act
        subject.onEnableFingerprintClicked()
        // Assert
        verify(mockBiometricsController).isBiometricAuthEnabled
        verifyNoMoreInteractions(mockBiometricsController)
        verify(mockPinRepository, times(3)).pin
        verifyNoMoreInteractions(mockPinRepository)
        verifyZeroInteractions(view)
    }

    @Test
    fun onEnableFingerprintClickedNoFingerprintEnrolled() {
        // Arrange
        whenever(mockBiometricsController.isBiometricAuthEnabled).thenReturn(false)
        whenever(mockBiometricsController.isHardwareDetected).thenReturn(true)
        // Act
        subject.onEnableFingerprintClicked()
        // Assert
        verify(mockBiometricsController).isBiometricAuthEnabled
        verify(mockBiometricsController).isHardwareDetected
        verifyNoMoreInteractions(mockBiometricsController)
        verify(view).showEnrollFingerprintsDialog()
        verifyNoMoreInteractions(view)
        verifyZeroInteractions(mockPinRepository)
    }

    @Test(expected = IllegalStateException::class)
    fun onEnableFingerprintClickedNoHardwareMethodCalledAccidentally() {
        // Arrange
        whenever(mockBiometricsController.isBiometricAuthEnabled).thenReturn(false)
        whenever(mockBiometricsController.isHardwareDetected).thenReturn(false)
        // Act
        subject.onEnableFingerprintClicked()
        // Assert
        verify(mockBiometricsController).isBiometricAuthEnabled
        verify(mockBiometricsController).isHardwareDetected
        verifyNoMoreInteractions(mockBiometricsController)
        verifyZeroInteractions(view)
        verifyZeroInteractions(mockPinRepository)
    }

    @Test
    fun setFingerprintUnlockEnabledFalse() {
        subject.setFingerprintUnlockDisabled()
        // Assert
        verify(mockBiometricsController).setBiometricUnlockDisabled()
        verifyNoMoreInteractions(mockBiometricsController)
    }

    @Test
    fun getEmail() {
        // Arrange
        val email = "EMAIL"
        subject.email = email
        // Act
        val result = subject.email
        // Assert
        result shouldEqual email
    }
}
