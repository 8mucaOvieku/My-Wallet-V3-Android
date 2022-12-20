package piuk.blockchain.android.ui.kyc.mobile.entry

import com.blockchain.android.testutils.rxInit
import com.blockchain.core.settings.PhoneNumber
import com.blockchain.core.settings.PhoneNumberUpdater
import com.blockchain.nabu.NabuUserSync
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.ui.kyc.mobile.entry.models.PhoneDisplayModel

class KycMobileEntryPresenterTest {

    private lateinit var subject: KycMobileEntryPresenter
    private val view: KycMobileEntryView = mock()
    private val phoneNumberUpdater: PhoneNumberUpdater = mock()
    private val nabuUserSync: NabuUserSync = mock {
        on { syncUser() }.thenReturn(Completable.complete())
    }

    @Suppress("unused")
    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = KycMobileEntryPresenter(phoneNumberUpdater, nabuUserSync)
        subject.initView(view)
    }

    @Test
    fun `onViewReady no phone number found, should not attempt to update UI`() {
        // Arrange
        whenever(phoneNumberUpdater.smsNumber()).thenReturn(Single.just(""))
        whenever(view.uiStateObservable).thenReturn(Observable.empty())
        // Act
        subject.onViewReady()
        // Assert
        verify(view).uiStateObservable
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `onViewReady phone number found, should attempt to update UI`() {
        // Arrange
        val phoneNumber = "+1234567890"
        whenever(phoneNumberUpdater.smsNumber()).thenReturn(Single.just(phoneNumber))
        whenever(view.uiStateObservable).thenReturn(Observable.empty())
        // Act
        subject.onViewReady()
        // Assert
        verify(view).preFillPhoneNumber(phoneNumber)
    }

    @Test
    fun `onViewReady, should sanitise input and progress page`() {
        // Arrange
        val phoneNumber = "+1 (234) 567-890"
        val phoneNumberSanitized = "+1234567890"
        val publishSubject = PublishSubject.create<Pair<PhoneNumber, Unit>>()
        whenever(phoneNumberUpdater.smsNumber()).thenReturn(Single.just(""))
        whenever(view.uiStateObservable).thenReturn(publishSubject)
        whenever(phoneNumberUpdater.updateSms(any())).thenReturn(Single.just("+1234567890"))
        // Act
        subject.onViewReady()
        publishSubject.onNext(PhoneNumber(phoneNumber) to Unit)
        // Assert
        verify(phoneNumberUpdater).updateSms(argThat { sanitized == "+1234567890" })
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).continueSignUp(PhoneDisplayModel(phoneNumber, phoneNumberSanitized))
        verify(nabuUserSync).syncUser()
    }

    @Test
    fun `onViewReady, should throw exception and resubscribe for next event`() {
        // Arrange
        val phoneNumber = "+1 (234) 567-890"
        val phoneNumberSanitized = "+1234567890"
        val publishSubject = PublishSubject.create<Pair<PhoneNumber, Unit>>()
        whenever(phoneNumberUpdater.smsNumber()).thenReturn(Single.just(""))
        whenever(view.uiStateObservable).thenReturn(publishSubject)
        whenever(phoneNumberUpdater.updateSms(any()))
            .thenReturn(Single.error { Throwable() })
            .thenReturn(Single.just("+1234567890"))
        // Act
        subject.onViewReady()
        publishSubject.onNext(PhoneNumber(phoneNumber) to Unit)
        publishSubject.onNext(PhoneNumber(phoneNumber) to Unit)
        // Assert
        verify(phoneNumberUpdater, times(2)).updateSms(argThat { sanitized == "+1234567890" })
        verify(view, times(2)).showProgressDialog()
        verify(view, times(2)).dismissProgressDialog()
        verify(view).showErrorSnackbar(any())
        verify(view).continueSignUp(PhoneDisplayModel(phoneNumber, phoneNumberSanitized))
        verify(nabuUserSync).syncUser()
    }

    @Test
    fun `onViewReady, should throw exception and display toast`() {
        // Arrange
        val phoneNumber = "+1 (234) 567-890"
        val publishSubject = PublishSubject.create<Pair<PhoneNumber, Unit>>()
        whenever(phoneNumberUpdater.smsNumber()).thenReturn(Single.just(""))
        whenever(view.uiStateObservable).thenReturn(publishSubject)
        whenever(phoneNumberUpdater.updateSms(any()))
            .thenReturn(Single.error { Throwable() })
        // Act
        subject.onViewReady()
        publishSubject.onNext(PhoneNumber(phoneNumber) to Unit)
        // Assert
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).showErrorSnackbar(any())
    }
}
