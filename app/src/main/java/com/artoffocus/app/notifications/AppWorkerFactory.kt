package com.artoffocus.app.notifications

import android.content.Context
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.artoffocus.app.AppContainer

class AppWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            // Rest end is not here: it needs to fire on time, so it is an exact AlarmManager
            // alarm delivered to RestEndReceiver rather than a (deliberately inexact) worker.
            DailyReminderWorker::class.java.name -> DailyReminderWorker(appContext, workerParameters, container)
            StreakRiskWorker::class.java.name -> StreakRiskWorker(appContext, workerParameters, container)
            else -> null
        }
}
