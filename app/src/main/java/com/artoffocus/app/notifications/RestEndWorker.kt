package com.artoffocus.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.artoffocus.app.AppContainer
import com.artoffocus.app.domain.focus.SessionStatus

/**
 * F14.1 — the one reliable notification kind. Fires only if rest is still in progress for
 * the same rest period it was scheduled for (the user may have already continued early once
 * the lock expired, or cancelled the whole session) — never during an active sitting (I7).
 */
class RestEndWorker(context: Context, params: WorkerParameters, private val container: AppContainer) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val expectedRestStartedAt = inputData.getLong(KEY_REST_STARTED_AT, -1L)
        val pieces = inputData.getInt(KEY_PIECES, 0)
        val session = container.sessionRepository.load(pieces)

        val stillResting = session.status in setOf(SessionStatus.RESTING, SessionStatus.REST_COMPLETE) &&
            session.restStartedAt == expectedRestStartedAt

        if (stillResting && container.notificationPrefsRepository.load().restEnd) {
            AppNotifier.notifyRestEnd(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val KEY_REST_STARTED_AT = "restStartedAt"
        const val KEY_PIECES = "pieces"
        const val UNIQUE_WORK_NAME = "rest_end"
    }
}
