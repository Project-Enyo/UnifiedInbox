package com.github.projectenyo.unifiedinbox.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.github.projectenyo.unifiedinbox.InboxEvent
import com.github.projectenyo.unifiedinbox.InboxLocation
import com.github.projectenyo.unifiedinbox.InboxOutgoing
import com.github.projectenyo.unifiedinbox.Protocol
import com.github.projectenyo.unifiedinbox.UnifiedInbox
import org.json.JSONObject
import java.util.concurrent.Executors

/** Receives the host's broadcasts. Only broadcasts that carry this app's registration token count. */
class ConnectorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(Protocol.EXTRA_TOKEN) ?: return
        if (token != UnifiedInbox.token(context)) return
        val host = UnifiedInbox.hostPackage(context) ?: return
        val app = context.applicationContext
        // Files can take longer than a receiver may block: they run on their own and end the broadcast right away.
        if (intent.action == Protocol.ACTION_FETCH_FILE) {
            files.execute { fetchFile(app, host, intent) }
            return
        }
        val pending = goAsync()
        executor.execute {
            try {
                val handler = UnifiedInbox.handler
                when (intent.action) {
                    Protocol.ACTION_REGISTERED -> {
                        UnifiedInbox.markRegistered(app, true)
                        handler?.onRegistered(app, host)
                    }
                    Protocol.ACTION_REGISTRATION_FAILED -> {
                        UnifiedInbox.markRegistered(app, false)
                        handler?.onRegistrationFailed(app, host, intent.getStringExtra(Protocol.EXTRA_REASON) ?: Protocol.REASON_INTERNAL_ERROR)
                    }
                    Protocol.ACTION_UNREGISTERED -> {
                        UnifiedInbox.markRegistered(app, false)
                        handler?.onUnregistered(app, host)
                    }
                    Protocol.ACTION_REPLY -> {
                        val conversation = intent.getStringExtra(Protocol.EXTRA_CONVERSATION) ?: return@execute
                        val request = intent.getStringExtra(Protocol.EXTRA_REQUEST) ?: return@execute
                        val text = intent.getStringExtra(Protocol.EXTRA_TEXT).orEmpty()
                        val attachments = uris(intent)
                        val location = intent.getStringExtra(Protocol.EXTRA_LOCATION)?.let { json ->
                            runCatching {
                                JSONObject(json).let { InboxLocation(it.getDouble("lat"), it.getDouble("lng"), name = it.optString("name").ifEmpty { null }) }
                            }.getOrNull()?.takeIf { it.lat in -90.0..90.0 && it.lng in -180.0..180.0 }
                        }
                        if (text.isBlank() && attachments.isEmpty() && location == null) return@execute
                        handler?.onSend(app, InboxOutgoing(conversation, request, text, intent.getStringExtra(Protocol.EXTRA_REPLY_TO), attachments, location))
                    }
                    Protocol.ACTION_MARK_READ -> {
                        val conversation = intent.getStringExtra(Protocol.EXTRA_CONVERSATION) ?: return@execute
                        handler?.onMarkRead(app, conversation, intent.getLongExtra(Protocol.EXTRA_TIME, System.currentTimeMillis()))
                    }
                    Protocol.ACTION_REACT -> {
                        val conversation = intent.getStringExtra(Protocol.EXTRA_CONVERSATION) ?: return@execute
                        val message = intent.getStringExtra(Protocol.EXTRA_MESSAGE) ?: return@execute
                        val emoji = intent.getStringExtra(Protocol.EXTRA_EMOJI)?.takeIf { it.isNotBlank() && it.length <= 32 } ?: return@execute
                        handler?.onReact(app, conversation, message, emoji, intent.getBooleanExtra(Protocol.EXTRA_REMOVE, false))
                    }
                    Protocol.ACTION_FETCH_IMAGE -> {
                        val id = intent.getStringExtra(Protocol.EXTRA_IMAGE)?.takeIf { it.isNotEmpty() } ?: return@execute
                        val image = runCatching { handler?.image(app, id) }.getOrNull()?.let(Images::encode)
                        UnifiedInbox.send(app, InboxEvent.Image(id, image))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun fetchFile(context: Context, host: String, intent: Intent) {
        val request = intent.getStringExtra(Protocol.EXTRA_REQUEST) ?: return
        val conversation = intent.getStringExtra(Protocol.EXTRA_CONVERSATION)
        val message = intent.getStringExtra(Protocol.EXTRA_MESSAGE)
        val attachment = intent.getStringExtra(Protocol.EXTRA_ATTACHMENT)
        val file = if (conversation == null || message == null || attachment == null) null
            else runCatching { UnifiedInbox.handler?.file(context, conversation, message, attachment) }.getOrNull()
        if (file == null || !file.file.isFile) {
            UnifiedInbox.send(context, InboxEvent.File(request, null, reason = "Not available"))
            return
        }
        val uri = InboxFileProvider.share(context, request, file)
        context.grantUriPermission(host, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        UnifiedInbox.send(context, InboxEvent.File(request, uri.toString(), file.mime, file.name ?: file.file.name, file.file.length()))
    }

    private fun uris(intent: Intent): List<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Protocol.EXTRA_ATTACHMENTS, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Protocol.EXTRA_ATTACHMENTS)
    }.getOrNull().orEmpty().filter { it.scheme == "content" }.take(10)

    private companion object {
        val executor = Executors.newSingleThreadExecutor()
        val files = Executors.newFixedThreadPool(2)
    }
}
