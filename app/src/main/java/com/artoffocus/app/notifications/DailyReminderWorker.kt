package com.artoffocus.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.artoffocus.app.AppContainer
import com.artoffocus.app.domain.focus.SessionStatus

/** F14.2 — best-effort on the web; reliable here. Off by default (DEFAULT_PREFS.dailyCall = false). */
class DailyReminderWorker(context: Context, params: WorkerParameters, private val container: AppContainer) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = container.notificationPrefsRepository.load()
        if (!prefs.dailyCall) return Result.success()

        // I7 — never fire while a sitting is actively running.
        val activePainting = container.artworkRepository.loadActive()
        if (activePainting != null) {
            val pieces = container.focusSetupRepository.load().let { it.totalMinutes / it.sittingMinutes }
            val session = container.sessionRepository.load(pieces)
            if (session.status == SessionStatus.RUNNING) return Result.success()
        }

        AppNotifier.notifyDailyCall(applicationContext)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_call"
    }
}
