package com.artoffocus.app.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    /** F14.1 — scheduled the moment rest starts; fires at the exact wall-clock rest end. */
    fun scheduleRestEnd(context: Context, restStartedAt: Long, restMinutes: Int, pieces: Int) {
        val fireAt = restStartedAt + restMinutes * 60_000L
        val delayMs = (fireAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<RestEndWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(RestEndWorker.KEY_REST_STARTED_AT, restStartedAt)
                    .putInt(RestEndWorker.KEY_PIECES, pieces)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RestEndWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelRestEnd(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(RestEndWorker.UNIQUE_WORK_NAME)
    }

    /** F14.2/F14.3 — best-effort on the web; scheduled as reliable daily periodic work here. */
    fun scheduleBestEffortReminders(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS).build(),
        )
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StreakRiskWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StreakRiskWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun rescheduleBestEffortReminders(context: Context) = scheduleBestEffortReminders(context)
}
