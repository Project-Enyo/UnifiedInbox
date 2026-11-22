package com.github.projectenyo.unifiedinbox

import android.content.Context
import android.content.Intent

/**
 * What a connector app does when its host talks to it. Set it once with
 * [UnifiedInbox.setHandler], in `Application.onCreate`, because the host may wake the app.
 * Callbacks run on a background thread and may block for a few seconds.
 */
interface InboxHandler {
    /** The host accepted the registration; a good moment to share recent history. */
    fun onRegistered(context: Context, host: String) {}

    fun onRegistrationFailed(context: Context, host: String, reason: String) {}

    /** The host or the person ended the registration. */
    fun onUnregistered(context: Context, host: String) {}

    /**
     * Sends [text] to [conversation] as if the person typed it in the app, then shares the sent
     * message with [InboxEvent.Message.request] set to [request], or reports [InboxEvent.ReplyFailed].
     */
    fun onReply(context: Context, conversation: String, text: String, request: String) {
        UnifiedInbox.send(context, InboxEvent.ReplyFailed(request, "Replies are not supported"))
    }

    /** Marks [conversation] read up to [time], with read receipts if the person enabled them. */
    fun onMarkRead(context: Context, conversation: String, time: Long) {}

    /** The intent that shows [conversation] in the app, or null to open the app's start screen. */
    fun openIntent(context: Context, conversation: String): Intent? = null
}
