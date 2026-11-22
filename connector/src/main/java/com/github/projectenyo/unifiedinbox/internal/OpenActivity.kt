package com.github.projectenyo.unifiedinbox.internal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.github.projectenyo.unifiedinbox.Protocol
import com.github.projectenyo.unifiedinbox.UnifiedInbox

/** Opens a conversation the host asked for, after checking the registration token. */
class OpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(Protocol.EXTRA_TOKEN)
        val conversation = intent.getStringExtra(Protocol.EXTRA_CONVERSATION)
        if (token != null && token == UnifiedInbox.token(this) && conversation != null) {
            val target = UnifiedInbox.handler?.openIntent(this, conversation) ?: packageManager.getLaunchIntentForPackage(packageName)
            if (target != null) runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
    }
}
