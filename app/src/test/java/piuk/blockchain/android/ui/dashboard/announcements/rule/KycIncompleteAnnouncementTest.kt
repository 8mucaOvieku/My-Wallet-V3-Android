package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.blockchain.nabu.UserIdentity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class KycIncompleteAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val userIdentity: UserIdentity = mock()
    private lateinit var subject: KycIncompleteAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[KycIncompleteAnnouncement.DISMISS_KEY])
            .thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey)
            .thenReturn(KycIncompleteAnnouncement.DISMISS_KEY)

        subject = KycIncompleteAnnouncement(
            userIdentity = userIdentity,
            dismissRecorder = dismissRecorder
        )
    }

    @Test
    fun `should not show, when already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, if kyc is not ongoing`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(userIdentity.isKycInProgress()).thenReturn(Single.just(false))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, if kyc is ongoing`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)
        whenever(userIdentity.isKycInProgress()).thenReturn(Single.just(true))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}
