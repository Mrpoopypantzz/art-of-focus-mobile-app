package com.artoffocus.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.artoffocus.app.AppContainer
import com.artoffocus.app.domain.focus.SessionStatus
import com.artoffocus.app.domain.notifications.shouldNotifyStreakRisk
import com.artoffocus.app.domain.profile.statistics

/** F14.3 — best-effort on the web; reliable here. Off by default. */
class StreakRiskWorker(context: Context, params: WorkerParameters, private val container: AppContainer) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = container.notificationPrefsRepository.load()
        if (!prefs.streakRisk) return Result.success()

        val paintings = container.museumRepository.getAll()
        val now = System.currentTimeMillis()
        val stats = statistics(paintings, now)
        val lastCompletedAt = paintings.maxOfOrNull { it.completedAt }

        if (!shouldNotifyStreakRisk(stats, lastCompletedAt, now)) return Result.success()

        // I7 — never fire while a sitting is actively running.
        val activePainting = container.artworkRepository.loadActive()
        if (activePainting != null) {
            val pieces = container.focusSetupRepository.load().let { it.totalMinutes / it.sittingMinutes }
            val session = container.sessionRepository.load(pieces)
            if (session.status == SessionStatus.RUNNING) return Result.success()
        }

        AppNotifier.notifyStreakRisk(applicationContext, stats.currentStreak)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "streak_risk"
    }
}
