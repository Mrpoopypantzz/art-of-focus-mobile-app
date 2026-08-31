package com.artoffocus.app

import android.app.Application
import androidx.work.Configuration
import com.artoffocus.app.notifications.AppWorkerFactory
import com.artoffocus.app.notifications.NotificationChannels
import com.artoffocus.app.notifications.NotificationScheduler

class ArtOfFocusApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
        NotificationScheduler.scheduleBestEffortReminders(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(container))
            .build()
}
