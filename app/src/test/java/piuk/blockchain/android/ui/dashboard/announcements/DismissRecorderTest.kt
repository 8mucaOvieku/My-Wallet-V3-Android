package piuk.blockchain.android.ui.dashboard.announcements

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ClassCastException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import piuk.blockchain.androidcore.utils.SessionPrefs

class DismissRecorderTest {

    private val prefs: SessionPrefs = mock()
    private val clock: DismissClock = mock()

    private lateinit var subject: DismissRecorder

    @Before
    fun setup() {
        subject = DismissRecorder(
            prefs = prefs,
            clock = clock
        )
    }

    @Test
    fun `entry is undismissed by default`() {
        whenever(prefs.getDismissalEntry(DISMISS_KEY)).thenReturn(0)
        whenever(clock.now()).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        assertFalse(entry.isDismissed)
    }

    @Test
    fun `persistent cards write to persistent prefs`() {

        whenever(clock.now()).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        entry.dismiss(DismissRule.CardPersistent)

        // Check that any historical value is removed:
        verify(prefs).deleteDismissalRecord(DISMISS_KEY)

        val captorValue = argumentCaptor<Long>()
        val captorString = argumentCaptor<String>()

        verify(prefs).recordDismissal(captorString.capture(), captorValue.capture())

        assertEquals(captorString.firstValue, DISMISS_KEY)
        assertEquals(captorValue.firstValue, Long.MAX_VALUE)

        verifyNoMoreInteractions(prefs)
    }

    @Test
    fun `periodic cards write to persistent prefs`() {

        whenever(clock.now()).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        entry.dismiss(DismissRule.CardPeriodic)

        // Check that any historical value is removed:
        verify(prefs).deleteDismissalRecord(DISMISS_KEY)

        val captorValue = argumentCaptor<Long>()
        val captorString = argumentCaptor<String>()

        verify(prefs).recordDismissal(captorString.capture(), captorValue.capture())

        assertEquals(captorString.firstValue, DISMISS_KEY)
        assertEquals(captorValue.firstValue, BASE_TIME + ONE_WEEK)

        verifyNoMoreInteractions(prefs)
    }

    @Test
    fun `one time cards write to persistent prefs`() {

        whenever(clock.now()).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        entry.dismiss(DismissRule.CardOneTime)

        // Check that any historical value is removed:
        verify(prefs).deleteDismissalRecord(DISMISS_KEY)

        val captorValue = argumentCaptor<Long>()
        val captorString = argumentCaptor<String>()

        verify(prefs).recordDismissal(captorString.capture(), captorValue.capture())

        assertEquals(captorString.firstValue, DISMISS_KEY)
        assertEquals(captorValue.firstValue, Long.MAX_VALUE)

        verifyNoMoreInteractions(prefs)
    }

    @Test
    fun `one time cards, once dismissed stay dismissed`() {

        whenever(clock.now()).thenReturn(BASE_TIME).thenReturn(BASE_TIME + ONE_WEEK)
        whenever(prefs.getDismissalEntry(DISMISS_KEY)).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        entry.dismiss(DismissRule.CardOneTime)

        val result = entry.isDismissed

        assertTrue(result)
    }

    @Test
    fun `periodic cards undismiss after defined interval`() {
        whenever(clock.now()).thenReturn(BASE_TIME + 1)
        whenever(prefs.getDismissalEntry(DISMISS_KEY)).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        val result = entry.isDismissed

        assertFalse(result)
    }

    @Test
    fun `boolean style dismissed is dismissed with new time-based call`() {
        whenever(prefs.getDismissalEntry(DISMISS_KEY)).thenThrow(ClassCastException("It's a boolean"))
        whenever(prefs.getLegacyDismissalEntry(DISMISS_KEY)).thenReturn(true)
        whenever(clock.now()).thenReturn(BASE_TIME)

        val entry = subject[DISMISS_KEY]

        val result = entry.isDismissed

        assertTrue(result)
    }

    @Test
    fun `undismiss clears all dismiss flags`() {
        val announcementList: AnnouncementList = mock()
        whenever(announcementList.dismissKeys()).thenReturn(listOf(DISMISS_KEY, DISMISS_KEY_2, DISMISS_KEY_3))

        subject[DISMISS_KEY].dismiss(DismissRule.CardPersistent)
        subject[DISMISS_KEY_2].dismiss(DismissRule.CardPeriodic)
        subject[DISMISS_KEY_3].dismiss(DismissRule.CardOneTime)

        subject.reinstateAllAnnouncements(announcementList)

        val captorString = argumentCaptor<String>()
        verify(prefs, times(6)).deleteDismissalRecord(captorString.capture())

        assertEquals(captorString.firstValue, DISMISS_KEY)
        assertEquals(captorString.secondValue, DISMISS_KEY_2)
        assertEquals(captorString.thirdValue, DISMISS_KEY_3)
    }

    companion object {
        private const val DISMISS_KEY = "wibble"
        private const val DISMISS_KEY_2 = "wobble"
        private const val DISMISS_KEY_3 = "wubble"

        private const val BASE_TIME = 10000000L
        private const val MINUTE = 60L * 1000L
        private const val ONE_WEEK = 7L * 24L * 60L * MINUTE
    }
}
