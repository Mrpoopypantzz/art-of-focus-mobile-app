package com.artoffocus.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val REST_END = "rest_end"
    const val DAILY_CALL = "daily_call"
    const val STREAK_RISK = "streak_risk"
    const val ACHIEVEMENT = "achievement"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(REST_END, "Rest complete", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(DAILY_CALL, "Daily studio call", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_RISK, "Streak protection", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(ACHIEVEMENT, "Achievements", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
