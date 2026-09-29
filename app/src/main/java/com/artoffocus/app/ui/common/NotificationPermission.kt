package com.artoffocus.app.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Asking for POST_NOTIFICATIONS, in the three places that need to ask.
 *
 * This was previously inline in Profile and *only* in Profile, which was the single largest
 * delivery gap in the app: a user could install it, run a full session, take a rest and never be
 * asked at all, because the request lived behind a tab they had no reason to open. Every
 * notification was then discarded by `AppNotifier.canPost` in complete silence.
 *
 * Extracted here because onboarding and the Focus screen now ask too, and three copies of this
 * logic would drift — particularly the part below that most implementations get wrong.
 *
 * **The permanent-denial problem.** Android shows the system dialog at most twice. After that,
 * `launch` returns instantly with a denial and no dialog ever appears, so a button wired
 * straight to `launch` becomes silently dead — it looks broken, and the user has no way to know
 * the real switch is now in system settings. [rememberNotificationPermission] detects that state
 * and [NotificationPermissionState.request] then opens the app's notification settings instead,
 * which is the only thing that works from that point on.
 */
class NotificationPermissionState internal constructor(
    /** True when notifications can be posted — always true below API 33, which has no runtime grant. */
    val granted: Boolean,
    /**
     * True when the system dialog will no longer appear, so asking again does nothing and the
     * user must be sent to settings. Callers may use this to soften the wording.
     */
    val mustUseSettings: Boolean,
    /** Shows the system dialog, or opens notification settings once that is futile. */
    val request: () -> Unit,
)

/**
 * Tracks the permission and re-checks it whenever the app returns to the foreground, so a change
 * made in system settings is picked up rather than leaving a stale "off" behind.
 */
@Composable
fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    val needsRuntimeGrant = Build.VERSION.SDK_INT >= 33

    fun isGranted(): Boolean = !needsRuntimeGrant || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    // Counts our own asks. Android gives no API for "will the dialog appear?", and
    // shouldShowRequestPermissionRationale is false both before the first ask and after a
    // permanent denial — identical values for opposite situations. Pairing it with "have we asked
    // at least once" is what tells the two apart.
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = isGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activity = context as? Activity
    val canStillShowDialog = activity == null || !asked ||
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    val mustUseSettings = needsRuntimeGrant && !granted && !canStillShowDialog

    return NotificationPermissionState(
        granted = granted,
        mustUseSettings = mustUseSettings,
        request = {
            when {
                !needsRuntimeGrant -> granted = true
                mustUseSettings -> openNotificationSettings(context)
                else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

/**
 * Opens this app's notification settings, falling back to its app-info page.
 *
 * The app-level and per-category switches both live here, so this is also the only useful
 * destination when a notification is blocked rather than merely ungranted. Previously the app
 * told the user to go to settings and gave them no way to get there.
 */
fun openNotificationSettings(context: Context) {
    val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(fallback) }
}
