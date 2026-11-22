package com.github.projectenyo.unifiedinbox.internal

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.github.projectenyo.unifiedinbox.Protocol

/** Asks the person's default host, or lets them pick one, through the hosts' link activities. */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(Protocol.LINK_URI))
        val started = runCatching { startActivityForResult(link, request) }.isSuccess
        if (!started) deliver(null)
    }

    @Deprecated("Activity result API of the platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != request) return
        val pi = if (resultCode != RESULT_OK || data == null) null
            else if (Build.VERSION.SDK_INT >= 33) data.getParcelableExtra(Protocol.EXTRA_PI, PendingIntent::class.java)
            else @Suppress("DEPRECATION") data.getParcelableExtra(Protocol.EXTRA_PI)
        deliver(pi?.creatorPackage)
    }

    private fun deliver(host: String?) {
        callback?.invoke(host)
        callback = null
        finish()
    }

    companion object {
        private const val request = 0x1b0c
        @Volatile private var callback: ((String?) -> Unit)? = null

        fun start(activity: Activity, result: (String?) -> Unit) {
            callback = result
            activity.startActivity(Intent(activity, LinkActivity::class.java))
        }
    }
}
