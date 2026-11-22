package com.github.projectenyo.unifiedinbox

import android.app.Activity
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.github.projectenyo.unifiedinbox.internal.LinkActivity
import org.json.JSONArray
import java.security.SecureRandom

/** An installed inbox host. [trusted] hosts may receive this app's chats. */
data class InboxHost(val packageName: String, val label: String, val trusted: Boolean)

/**
 * The connector side of UnifiedInbox: choose a host, register, share conversations as
 * [InboxEvent]s, and react to the host through an [InboxHandler]. See SPEC.md.
 *
 * ```
 * UnifiedInbox.setHandler(myHandler)                     // Application.onCreate
 * UnifiedInbox.tryUseDefaultHost(activity) { found -> }  // or useHost(context, chosen.packageName)
 * UnifiedInbox.register(context, setOf(Protocol.FEATURE_REPLY, Protocol.FEATURE_MARK_READ, Protocol.FEATURE_OPEN))
 * UnifiedInbox.send(context, InboxEvent.Message(...))
 * ```
 */
object UnifiedInbox {
    private const val store = "unifiedinbox_connector"
    private val defaultTrusted = setOf("space.koyu.enyo", "space.koyu.enyo.debug")
    private val privilegedDirectories = listOf("/system/priv-app/", "/product/priv-app/", "/system_ext/priv-app/", "/vendor/priv-app/")

    @Volatile internal var handler: InboxHandler? = null
        private set

    /**
     * Decides which hosts may receive this app's chats. By default the Enyo launcher and apps
     * installed in a priv-app directory of the system image.
     */
    @Volatile var isTrusted: (Context, String) -> Boolean = { context, packageName ->
        packageName in defaultTrusted || isPrivileged(context, packageName)
    }

    fun setHandler(handler: InboxHandler) {
        this.handler = handler
    }

    /** Every installed host, trusted ones first. */
    fun hosts(context: Context): List<InboxHost> {
        val pm = context.packageManager
        return queryHosts(context).map { packageName ->
            InboxHost(packageName, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName),
                isTrusted(context, packageName))
        }.sortedByDescending { it.trusted }
    }

    /** The host this app uses, or null. */
    fun host(context: Context): String? = prefs(context).getString("host", null)?.takeIf { it in queryHosts(context) }

    /** Whether the host accepted the current registration. */
    fun isRegistered(context: Context): Boolean = host(context) != null && prefs(context).getBoolean("registered", false)

    /** Chooses [packageName] as the host. Refuses hosts that are not trusted. */
    fun useHost(context: Context, packageName: String): Boolean {
        if (!isTrusted(context, packageName) || packageName !in queryHosts(context)) return false
        val previous = prefs(context).getString("host", null)
        if (previous != null && previous != packageName) unregister(context)
        prefs(context).edit().putString("host", packageName).apply()
        return true
    }

    /**
     * Uses the only trusted host, or the person's default host through the host's link activity.
     * [callback] receives whether a host is now chosen.
     */
    fun tryUseDefaultHost(activity: Activity, callback: (Boolean) -> Unit) {
        host(activity)?.let { callback(true); return }
        val trusted = hosts(activity).filter { it.trusted }
        when {
            trusted.size == 1 -> callback(useHost(activity, trusted.single().packageName))
            trusted.isEmpty() -> callback(false)
            else -> LinkActivity.start(activity) { packageName -> callback(packageName != null && useHost(activity, packageName)) }
        }
    }

    /** Registers with the chosen host, announcing the [features] this app handles. */
    fun register(context: Context, features: Set<String>, label: String? = null): Boolean {
        val host = host(context) ?: return false
        val token = prefs(context).getString("token", null) ?: newToken().also { prefs(context).edit().putString("token", it).apply() }
        prefs(context).edit().putStringSet("features", features).apply()
        broadcast(context, host, android.content.Intent(Protocol.ACTION_HOST_REGISTER)
            .putExtra(Protocol.EXTRA_TOKEN, token)
            .putExtra(Protocol.EXTRA_VERSION, Protocol.VERSION)
            .putExtra(Protocol.EXTRA_FEATURES, features.toTypedArray())
            .apply { if (label != null) putExtra(Protocol.EXTRA_LABEL, label) })
        return true
    }

    /** Ends the registration; the host deletes what this app shared. */
    fun unregister(context: Context) {
        val host = prefs(context).getString("host", null)
        val token = prefs(context).getString("token", null)
        if (host != null && token != null) broadcast(context, host, Intent(Protocol.ACTION_HOST_UNREGISTER).putExtra(Protocol.EXTRA_TOKEN, token))
        prefs(context).edit().remove("host").remove("token").remove("registered").apply()
    }

    /** Shares events with the host, split into several broadcasts when they are large. Returns false without a registration. */
    fun send(context: Context, vararg events: InboxEvent): Boolean = send(context, events.toList())

    fun send(context: Context, events: List<InboxEvent>): Boolean {
        if (events.isEmpty()) return true
        if (!isRegistered(context)) return false
        val host = host(context) ?: return false
        val token = prefs(context).getString("token", null) ?: return false
        var batch = JSONArray()
        var size = 2
        fun flush() {
            if (batch.length() == 0) return
            broadcast(context, host, Intent(Protocol.ACTION_HOST_EVENT).putExtra(Protocol.EXTRA_TOKEN, token).putExtra(Protocol.EXTRA_EVENTS, batch.toString()))
            batch = JSONArray()
            size = 2
        }
        for (event in events) {
            val json = event.toJson()
            val length = json.toString().length * 2
            if (size + length > Protocol.MAX_EVENTS_BYTES) flush()
            batch.put(json)
            size += length + 1
        }
        flush()
        return true
    }

    internal fun token(context: Context): String? = prefs(context).getString("token", null)

    internal fun markRegistered(context: Context, registered: Boolean) {
        prefs(context).edit().putBoolean("registered", registered).apply()
        if (!registered) prefs(context).edit().remove("token").apply()
    }

    internal fun hostPackage(context: Context): String? = prefs(context).getString("host", null)

    private fun broadcast(context: Context, host: String, intent: Intent) {
        intent.setPackage(host).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        if (Build.VERSION.SDK_INT >= 34) {
            context.sendBroadcast(intent, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
        } else {
            val identity = PendingIntent.getBroadcast(context, 0, Intent("${context.packageName}.unifiedinbox.identity").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
            context.sendBroadcast(intent.putExtra(Protocol.EXTRA_PI, identity))
        }
    }

    private fun queryHosts(context: Context): List<String> = runCatching {
        val intent = Intent(Protocol.ACTION_HOST_REGISTER)
        val found = if (Build.VERSION.SDK_INT >= 33) context.packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0))
            else @Suppress("DEPRECATION") context.packageManager.queryBroadcastReceivers(intent, 0)
        found.filter { it.activityInfo.exported }.map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
    }.getOrDefault(emptyList())

    private fun isPrivileged(context: Context, packageName: String): Boolean {
        val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return false
        return info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && privilegedDirectories.any { info.sourceDir.orEmpty().startsWith(it) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(store, Context.MODE_PRIVATE)

    private fun newToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }
}
