package com.github.projectenyo.unifiedinbox.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.projectenyo.unifiedinbox.Protocol
import com.github.projectenyo.unifiedinbox.UnifiedInbox
import java.util.concurrent.Executors

/** Receives the host's broadcasts. Only broadcasts that carry this app's registration token count. */
class ConnectorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(Protocol.EXTRA_TOKEN) ?: return
        if (token != UnifiedInbox.token(context)) return
        val host = UnifiedInbox.hostPackage(context) ?: return
        val app = context.applicationContext
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
                        val text = intent.getStringExtra(Protocol.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return@execute
                        val request = intent.getStringExtra(Protocol.EXTRA_REQUEST) ?: return@execute
                        handler?.onReply(app, conversation, text, request)
                    }
                    Protocol.ACTION_MARK_READ -> {
                        val conversation = intent.getStringExtra(Protocol.EXTRA_CONVERSATION) ?: return@execute
                        handler?.onMarkRead(app, conversation, intent.getLongExtra(Protocol.EXTRA_TIME, System.currentTimeMillis()))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val executor = Executors.newSingleThreadExecutor()
    }
}
