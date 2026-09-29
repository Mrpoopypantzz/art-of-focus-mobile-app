package com.artoffocus.app

import android.app.Application
import androidx.work.Configuration
import com.artoffocus.app.notifications.AppWorkerFactory
import com.artoffocus.app.notifications.NotificationChannels
import com.artoffocus.app.notifications.NotificationScheduler
import com.artoffocus.app.domain.focus.MAX_PIECES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArtOfFocusApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
        NotificationScheduler.scheduleBestEffortReminders(this)
        reconcileRestAlarm()
    }

    /**
     * Re-arms the rest-end alarm if a rest is still in progress.
     *
     * A force-stop — whether from Settings or from an OEM battery "cleanup" sweep, which on some
     * skins happens minutes after the app leaves the screen — cancels every pending alarm and
     * broadcasts nothing, so [BootRescheduleReceiver] never hears about it. The same is true of
     * the exact-alarm permission being revoked on API 31/32. Without this, a rest begun before
     * any of those lost its notification permanently and silently.
     *
     * Deliberately not on the timer's path (I7): this only ever re-arms a notification, and
     * `reconcileRestEnd` does nothing at all unless a rest is genuinely still running.
     */
    private fun reconcileRestAlarm() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                val session = container.sessionRepository.load(MAX_PIECES)
                NotificationScheduler.reconcileRestEnd(this@ArtOfFocusApp, session, MAX_PIECES)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(container))
            .build()
}
