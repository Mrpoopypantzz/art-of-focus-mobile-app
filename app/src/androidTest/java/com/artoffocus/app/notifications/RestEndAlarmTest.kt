package com.artoffocus.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artoffocus.app.domain.focus.IDLE_SESSION
import com.artoffocus.app.domain.focus.SessionState
import com.artoffocus.app.domain.focus.SessionStatus
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The guard on [NotificationScheduler.reconcileRestEnd], the re-arm that runs on every app launch.
 *
 * Why this is needed: a force-stop — from Settings, or from an OEM battery "cleanup" sweep, which
 * on some skins happens within minutes of the app leaving the screen — cancels every pending alarm
 * and broadcasts nothing at all, so [BootRescheduleReceiver] never hears about it. Before this
 * existed, a rest begun before such a kill lost its notification permanently and silently.
 *
 * It must re-arm in exactly one case and stay out of the way in every other: an alarm resurrected
 * for a finished or absent rest would wake the device for nothing.
 *
 * Existence is checked with `PendingIntent.FLAG_NO_CREATE`, which returns null when no matching
 * PendingIntent is registered — the only way to observe an alarm from a test without a shadow
 * framework.
 */
@RunWith(AndroidJUnit4::class)
class RestEndAlarmTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearAlarm()
    }

    @After
    fun tearDown() = clearAlarm()

    private fun clearAlarm() {
        NotificationScheduler.cancelRestEnd(context)
        existing()?.cancel()
    }

    /** Null when no rest-end alarm is currently registered. */
    private fun existing(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        RestEndReceiver.REQUEST_CODE,
        Intent(context, RestEndReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun resting(startedAgoMs: Long, restMinutes: Int) = SessionState(
        status = SessionStatus.RESTING,
        sittingIndex = 1,
        restStartedAt = System.currentTimeMillis() - startedAgoMs,
        restMinutes = restMinutes,
    )

    @Test
    fun a_rest_still_in_progress_gets_its_alarm_back() {
        // Two minutes into a ten-minute rest: this is the case the whole mechanism exists for.
        NotificationScheduler.reconcileRestEnd(context, resting(startedAgoMs = 2 * 60_000L, restMinutes = 10), pieces = 4)
        assertNotNull("a rest in progress must have its alarm re-armed", existing())
    }

    @Test
    fun a_rest_that_already_ended_is_left_alone() {
        // Twenty minutes into a ten-minute rest. Re-arming here would schedule an alarm in the
        // past, waking the device to post a notification the user no longer needs.
        NotificationScheduler.reconcileRestEnd(context, resting(startedAgoMs = 20 * 60_000L, restMinutes = 10), pieces = 4)
        assertNull("a finished rest must not be re-armed", existing())
    }

    @Test
    fun an_idle_session_arms_nothing() {
        NotificationScheduler.reconcileRestEnd(context, IDLE_SESSION, pieces = 4)
        assertNull(existing())
    }

    @Test
    fun a_running_sitting_arms_nothing() {
        // The rest alarm belongs to rest alone. A sitting in progress has no rest end to fire at,
        // and restMinutes is 0 here — arming would mean an alarm due immediately.
        val running = SessionState(status = SessionStatus.RUNNING, startedAt = System.currentTimeMillis())
        NotificationScheduler.reconcileRestEnd(context, running, pieces = 4)
        assertNull(existing())
    }

    @Test
    fun a_completed_rest_arms_nothing() {
        // I11 — REST_COMPLETE is a settled state waiting on the user's tap. The notification for
        // it has already been posted (or declined); re-arming would post it twice.
        val done = SessionState(status = SessionStatus.REST_COMPLETE, sittingIndex = 1, restMinutes = 10)
        NotificationScheduler.reconcileRestEnd(context, done, pieces = 4)
        assertNull(existing())
    }

    @Test
    fun scheduling_then_cancelling_leaves_nothing_behind() {
        // Completing or abandoning a painting cancels this alarm; if cancel did not really match
        // the registered PendingIntent, the device would keep waking for a dead session.
        NotificationScheduler.scheduleRestEnd(
            context = context,
            restStartedAt = System.currentTimeMillis(),
            restMinutes = 10,
            pieces = 4,
            sittingIndex = 1,
        )
        assertNotNull(existing())

        NotificationScheduler.cancelRestEnd(context)
        assertNull("cancel must match and remove the armed alarm", existing())
    }

    @Test
    fun rescheduling_replaces_rather_than_stacks() {
        // FLAG_UPDATE_CURRENT and a single request code mean there is only ever one rest-end
        // alarm. Two rests in a row must not leave the first one armed to fire at the wrong time.
        NotificationScheduler.scheduleRestEnd(context, System.currentTimeMillis(), 10, 4, sittingIndex = 1)
        NotificationScheduler.scheduleRestEnd(context, System.currentTimeMillis(), 5, 4, sittingIndex = 2)
        assertNotNull(existing())

        // One cancel is enough to clear it, which is only true if there is exactly one.
        NotificationScheduler.cancelRestEnd(context)
        assertNull(existing())
    }

    @Test
    fun the_alarm_manager_is_reachable_at_all() {
        // Guards the early `?: return` in the scheduler: if the service were unavailable, every
        // test above would pass by doing nothing.
        assertNotNull(context.getSystemService(AlarmManager::class.java))
    }
}
