package com.artoffocus.app.notifications

/**
 * Turns a [AppNotifier.PostResult] into something a person can act on.
 *
 * Kept out of the Compose screen so it can be tested directly. The wording matters more than
 * usual here: this text exists because the app previously failed silently, and a message that
 * leaked an enum name or a stack trace would leave the user no better off than the silence did.
 * Every branch names what is wrong AND where to change it.
 */
fun describeDelivery(result: AppNotifier.PostResult, unrestricted: Boolean): String = when (result) {
    AppNotifier.PostResult.Posted ->
        if (unrestricted) {
            "Sent — check your notification shade."
        } else {
            // Posting succeeded, so this is not a failure — but on a phone that can freeze the
            // app in the background, "it worked here" is not the same as "it will arrive while
            // you rest". Saying so is more honest than a bare success.
            "Sent, but your phone may still hold it back in the background — see below."
        }
    AppNotifier.PostResult.PermissionMissing ->
        "Blocked: this app doesn't have permission to send notifications yet."
    AppNotifier.PostResult.AppBlocked ->
        "Blocked: notifications are turned off for Art of Focus in your phone's settings."
    AppNotifier.PostResult.ChannelBlocked ->
        "Blocked: the \"Rest complete\" category is turned off in your phone's settings."
    is AppNotifier.PostResult.Failed ->
        "Your phone refused to show it. Check that notifications are allowed for this app."
}
