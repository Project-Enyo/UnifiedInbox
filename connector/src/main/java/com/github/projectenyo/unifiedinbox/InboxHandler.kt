package com.github.projectenyo.unifiedinbox

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri

/** What the host asks a connector to send: text, optionally quoting [replyTo], with files or a place. */
data class InboxOutgoing(
    val conversation: String,
    val request: String,
    /** The text, or the caption of [attachments]; may be empty with attachments or a location. */
    val text: String,
    val replyTo: String? = null,
    /** Files the host granted this app read access to. */
    val attachments: List<Uri> = emptyList(),
    val location: InboxLocation? = null
)

/** A file the connector shares with the host for [InboxHandler.file]. */
data class InboxFile(val file: java.io.File, val mime: String? = null, val name: String? = null)

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

    /**
     * Sends what the host composed. Override it to support [Protocol.FEATURE_REPLY_TO],
     * [Protocol.FEATURE_SEND_ATTACHMENTS] or [Protocol.FEATURE_SEND_LOCATION]; by default plain
     * text goes to [onReply] and anything else fails.
     */
    fun onSend(context: Context, outgoing: InboxOutgoing) {
        if (outgoing.attachments.isEmpty() && outgoing.location == null) onReply(context, outgoing.conversation, outgoing.text, outgoing.request)
        else UnifiedInbox.send(context, InboxEvent.ReplyFailed(outgoing.request, "Attachments are not supported"))
    }

    /** Marks [conversation] read up to [time], with read receipts if the person enabled them. */
    fun onMarkRead(context: Context, conversation: String, time: Long) {}

    /** Adds the person's [emoji] reaction to [message], or takes it back with [remove], then shares an [InboxEvent.Reaction]. */
    fun onReact(context: Context, conversation: String, message: String, emoji: String, remove: Boolean) {}

    /**
     * Loads the image an image id names, such as [InboxMember.avatar] or [InboxAttachment.thumbnail],
     * or returns null when it is not available. The library scales, encodes and sends it. Only serve
     * images of what the app shares.
     */
    fun image(context: Context, id: String): Bitmap? = null

    /**
     * The decrypted or downloaded content of an [InboxAttachment], or null when it is not available.
     * The library shares the file with the host through its own provider. Only serve files of what the
     * app shares. May take a while; the host waits for it.
     */
    fun file(context: Context, conversation: String, message: String, attachment: String): InboxFile? = null

    /** The intent that shows [conversation] in the app, or null to open the app's start screen. */
    fun openIntent(context: Context, conversation: String): Intent? = null
}
