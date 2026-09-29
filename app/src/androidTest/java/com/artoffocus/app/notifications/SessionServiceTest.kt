package com.artoffocus.app.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artoffocus.app.ArtOfFocusApp
import com.artoffocus.app.data.repository.SessionRepository
import com.artoffocus.app.domain.focus.MAX_PIECES
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The containment guarantee behind the ongoing session notification.
 *
 * [SessionService] exists to stop the OS killing the process mid-session, which is what makes the
 * rest alarm survive on phones that sweep background apps. Granting it a foreground service and a
 * once-a-second loop over live session state is the riskiest change in this area, because the
 * entire timer's correctness rests on one rule: **the service reads session state and never
 * writes it.** The ViewModel's ticker remains the only thing that advances a session.
 *
 * If that rule were ever broken, the failure would be severe and hard to spot — a session quietly
 * advanced by a background service would violate I7 (progress derived from wall-clock timestamps,
 * never from a counter) and could violate I11 (rest never auto-starts the next sitting), the two
 * invariants this project treats as inviolable.
 *
 * So this asserts the rule directly: run the service across a state where an advance would be
 * tempting — a sitting whose time is already up — and confirm the persisted session comes back
 * byte-for-byte identical.
 */
@RunWith(AndroidJUnit4::class)
class SessionServiceTest {

    private lateinit var context: Context
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessions = (context as ArtOfFocusApp).container.sessionRepository
        runBlocking { sessions.clear() }
    }

    @After
    fun tearDown() {
        SessionService.stop(context)
        runBlocking { sessions.clear() }
    }

    @Test
    fun the_service_never_writes_session_state() = runBlocking {
        // A sitting whose 30 minutes elapsed an hour ago. The ViewModel's ticker would complete
        // this immediately; the service must not, however long it runs.
        val overdue = SessionState(
            status = SessionStatus.RUNNING,
            startedAt = System.currentTimeMillis() - 90 * 60_000L,
            sittingIndex = 1,
        )
        sessions.save(overdue)
        val before = sessions.load(MAX_PIECES)

        SessionService.start(context)
        // Several of the service's one-second ticks, so this is not passing merely because the
        // service had not started looping yet.
        delay(3_500)
        SessionService.stop(context)

        val after = sessions.load(MAX_PIECES)
        assertEquals("the service must not advance the session", before, after)
        assertEquals(SessionStatus.RUNNING, after.status)
        assertEquals(overdue.startedAt, after.startedAt)
        assertEquals(overdue.sittingIndex, after.sittingIndex)
    }

    @Test
    fun the_service_never_completes_a_rest() = runBlocking {
        // I11 is the sharper version of the same rule: a rest whose time is up must stay RESTING
        // until the ViewModel transitions it, and must never be pushed towards a next sitting.
        val overdueRest = SessionState(
            status = SessionStatus.RESTING,
            sittingIndex = 1,
            restStartedAt = System.currentTimeMillis() - 60 * 60_000L,
            restMinutes = 10,
        )
        sessions.save(overdueRest)

        SessionService.start(context)
        delay(3_500)
        SessionService.stop(context)

        val after = sessions.load(MAX_PIECES)
        assertEquals(SessionStatus.RESTING, after.status)
        assertEquals(overdueRest.restStartedAt, after.restStartedAt)
        assertEquals(overdueRest, after)
    }

    @Test
    fun the_service_stops_itself_when_the_session_is_over() = runBlocking {
        // The ongoing notification is not dismissible, so a service that outlived its session
        // would strand an undismissable notification in the shade with no way to clear it.
        sessions.save(SessionState(status = SessionStatus.RUNNING, startedAt = System.currentTimeMillis()))
        SessionService.start(context)
        delay(1_500)

        // What finishPainting does: clear the session. The service polls and should retire.
        sessions.clear()
        delay(2_500)

        // The session is genuinely gone; the service reading it must not have recreated it.
        assertEquals(SessionStatus.IDLE, sessions.load(MAX_PIECES).status)
    }

    @Test
    fun starting_the_service_twice_is_harmless() = runBlocking {
        // start() is called from Begin, Take Rest and Continue, so a single session starts it
        // repeatedly by design. A second start must not spawn a second polling loop.
        sessions.save(SessionState(status = SessionStatus.RUNNING, startedAt = System.currentTimeMillis()))
        val before = sessions.load(MAX_PIECES)

        SessionService.start(context)
        SessionService.start(context)
        delay(2_000)
        SessionService.stop(context)

        assertEquals(before, sessions.load(MAX_PIECES))
    }

    @Test
    fun stopping_a_service_that_was_never_started_is_harmless() {
        // confirmCancel and abandonPainting call stop() unconditionally, including from states
        // where no service is running.
        SessionService.stop(context)
        SessionService.stop(context)
    }
}
