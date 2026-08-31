package com.artoffocus.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * A native app can deliver these reliably regardless of app/process state — unlike the web
 * version, which had to distinguish "reliable" (rest end, achievement) from "best effort"
 * (daily call, streak risk) due to background-tab and PWA-install limitations. Every kind is
 * reliable here.
 */
object AppNotifier {

    private fun canPost(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notify(context: Context, channelId: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun notifyRestEnd(context: Context) {
        notify(context, NotificationChannels.REST_END, id = 1, title = "Rest complete", text = "Ready for the next sitting whenever you are.")
    }

    fun notifyDailyCall(context: Context) {
        notify(context, NotificationChannels.DAILY_CALL, id = 2, title = "The studio is open", text = "A sitting is waiting whenever you have time today.")
    }

    fun notifyStreakRisk(context: Context, currentStreak: Int) {
        notify(
            context,
            NotificationChannels.STREAK_RISK,
            id = 3,
            title = "Keep your streak going",
            text = "You're on a $currentStreak-day streak — a quick sitting today keeps it alive.",
        )
    }

    fun notifyAchievement(context: Context, title: String) {
        notify(context, NotificationChannels.ACHIEVEMENT, id = 4, title = "Achievement unlocked", text = title)
    }
}
