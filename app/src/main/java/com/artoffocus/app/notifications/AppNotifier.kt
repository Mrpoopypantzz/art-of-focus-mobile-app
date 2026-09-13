package com.artoffocus.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.artoffocus.app.R

/**
 * Posts the app's notifications, and says what happened when one does not arrive.
 *
 * This used to fail silently in three separate ways: the permission check returned early, any
 * posting error was swallowed by `runCatching`, and nothing ever asked whether notifications
 * were switched off at the app or channel level. The result was that "blocked", "rejected by
 * the phone" and "working" all looked identical from the outside, which made a real delivery
 * bug on a physical device very hard to place. Every path now yields a [PostResult], and the
 * Profile screen's self-test surfaces it in plain words.
 */
object AppNotifier {

    /** Why a notification did or did not reach the shade. */
    sealed interface PostResult {
        data object Posted : PostResult

        /** The runtime permission was never granted (Android 13+). */
        data object PermissionMissing : PostResult

        /** Notifications are switched off for the whole app in system settings. */
        data object AppBlocked : PostResult

        /** This one category is switched off, even though the app is allowed. */
        data object ChannelBlocked : PostResult

        /** The platform rejected the post; [reason] is for logs, never for the user. */
        data class Failed(val reason: String) : PostResult
    }

    private const val TAG = "AppNotifier"

    private fun canPost(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when the user has turned this specific category off. Checked separately from the
     * app-wide switch because the two are independent: a user can allow the app and still
     * silence "Rest complete", and only the channel check explains that case.
     */
    private fun channelBlocked(context: Context, channelId: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 26) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    private fun notify(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        text: String,
    ): PostResult {
        val result = when {
            !canPost(context) -> PostResult.PermissionMissing
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> PostResult.AppBlocked
            channelBlocked(context, channelId) -> PostResult.ChannelBlocked
            else -> {
                val notification = NotificationCompat.Builder(context, channelId)
                    // A flat monochrome mask, not a framework dialog asset — see
                    // res/drawable/ic_notification.xml for why that distinction matters.
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
                    .fold(
                        onSuccess = { PostResult.Posted },
                        onFailure = { PostResult.Failed(it.toString()) },
                    )
            }
        }
        // Logged so a future delivery problem is diagnosable from logcat rather than by
        // bisecting builds, which is how this one had to be found.
        if (result != PostResult.Posted) Log.w(TAG, "notification not posted: $result (channel=$channelId)")
        return result
    }

    fun notifyRestEnd(context: Context): PostResult = notify(
        context,
        NotificationChannels.REST_END,
        id = 1,
        title = "Rest complete",
        text = "Ready for the next sitting whenever you are.",
    )

    fun notifyDailyCall(context: Context): PostResult = notify(
        context,
        NotificationChannels.DAILY_CALL,
        id = 2,
        title = "The studio is open",
        text = "A sitting is waiting whenever you have time today.",
    )

    fun notifyStreakRisk(context: Context, currentStreak: Int): PostResult = notify(
        context,
        NotificationChannels.STREAK_RISK,
        id = 3,
        title = "Keep your streak going",
        text = "You're on a $currentStreak-day streak — a quick sitting today keeps it alive.",
    )

    fun notifyAchievement(context: Context, title: String): PostResult = notify(
        context,
        NotificationChannels.ACHIEVEMENT,
        id = 4,
        title = "Achievement unlocked",
        text = title,
    )

    /**
     * Posts a real notification through the real path so the user can see whether delivery
     * works, without sitting through a ten-minute rest. Uses the rest-end channel deliberately:
     * testing a different channel would prove nothing about the one that matters.
     */
    fun sendTestNotification(context: Context): PostResult = notify(
        context,
        NotificationChannels.REST_END,
        id = 99,
        title = "Notifications are working",
        text = "This is what you'll see when a rest ends.",
    )
}
