package com.github.projectenyo.unifiedinbox

import org.json.JSONArray
import org.json.JSONObject

/**
 * A person in a conversation. [phone] and [uri] help the host link them to the address book.
 * [avatar] is the image id of their picture, see Images in SPEC.md; null when they have none.
 */
data class InboxMember(val id: String, val name: String? = null, val phone: String? = null, val uri: String? = null, val avatar: String? = null)

/**
 * A file in a message. [kind] is one of image, video, gif, sticker, voice, audio and file.
 * [thumbnail] is an image id; the host fetches the content through [InboxHandler.file].
 */
data class InboxAttachment(
    val id: String,
    val kind: String,
    val name: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Milliseconds. */
    val duration: Long? = null,
    /** Levels from 0 to 1, for voice messages. */
    val waveform: List<Float>? = null,
    val thumbnail: String? = null,
    val spoiler: Boolean = false
) {
    internal fun toJson() = JSONObject().put("id", id).put("kind", kind).putOpt("name", name).putOpt("mime", mime)
        .putOpt("size", size).putOpt("width", width).putOpt("height", height).putOpt("duration", duration)
        .putOpt("waveform", waveform?.let { levels -> JSONArray(levels.map { it.toDouble() }) })
        .putOpt("thumbnail", thumbnail).apply { if (spoiler) put("spoiler", true) }

    companion object {
        const val IMAGE = "image"
        const val VIDEO = "video"
        const val GIF = "gif"
        const val STICKER = "sticker"
        const val VOICE = "voice"
        const val AUDIO = "audio"
        const val FILE = "file"
    }
}

/** A link preview; [image] is an image id. */
data class InboxPreview(val url: String, val title: String? = null, val description: String? = null, val site: String? = null, val image: String? = null) {
    internal fun toJson() = JSONObject().put("url", url).putOpt("title", title).putOpt("description", description)
        .putOpt("site", site).putOpt("image", image)
}

/** A song; [image] is an image id. */
data class InboxMusic(val artist: String, val title: String, val url: String? = null, val image: String? = null) {
    internal fun toJson() = JSONObject().put("artist", artist).put("title", title).putOpt("url", url).putOpt("image", image)
}

/** A place. [live] locations keep moving through [InboxEvent.LocationUpdate] until [until]. */
data class InboxLocation(
    val lat: Double,
    val lng: Double,
    val accuracy: Float? = null,
    val name: String? = null,
    val live: Boolean = false,
    val until: Long? = null
) {
    internal fun toJson() = JSONObject().put("lat", lat).put("lng", lng).putOpt("accuracy", accuracy?.toDouble())
        .putOpt("name", name).apply { if (live) put("live", true) }.putOpt("until", until)
}

/** One event a connector shares with its host, see the Events section of SPEC.md. */
sealed class InboxEvent {
    internal abstract fun toJson(): JSONObject

    data class Conversation(
        val id: String,
        val title: String? = null,
        val group: Boolean = false,
        /** In a one to one chat, only the other person. */
        val members: List<InboxMember> = emptyList(),
        val muted: Boolean = false,
        /** The shortcut id the app sets on this chat's notifications, so hosts recognize the chat. */
        val shortcut: String? = null,
        /** For a group, the image id of its picture; one to one chats use the member's picture. */
        val avatar: String? = null
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "conversation").put("id", id).putOpt("title", title).putOpt("shortcut", shortcut)
            .putOpt("avatar", avatar).put("group", group).put("muted", muted)
            .put("members", JSONArray(members.map {
                JSONObject().put("id", it.id).putOpt("name", it.name).putOpt("phone", it.phone).putOpt("uri", it.uri).putOpt("avatar", it.avatar)
            }))
    }

    data class Message(
        val conversation: String,
        val id: String,
        /** Plain text; messages without text carry a short description such as "Photo". */
        val text: String,
        val time: Long,
        val outgoing: Boolean = false,
        /** The member id of the sender; null for the person's own messages. */
        val sender: String? = null,
        val senderName: String? = null,
        val replyTo: String? = null,
        /** The request id of the host reply this message answers. */
        val request: String? = null,
        /** The text with formatting, in CommonMark; `||text||` is a spoiler. */
        val markdown: String? = null,
        val attachments: List<InboxAttachment> = emptyList(),
        val previews: List<InboxPreview> = emptyList(),
        val music: InboxMusic? = null,
        val location: InboxLocation? = null,
        /** View once: the host shows only that there is such a message. */
        val once: Boolean = false,
        /** A notice in the chat, such as someone joining. */
        val system: Boolean = false,
        /** [text] only describes the attachments, music or place, like "Photo" for a picture without a caption. */
        val description: Boolean = false
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "message").put("conversation", conversation).put("id", id)
            .put("text", text).put("time", time).put("outgoing", outgoing).putOpt("sender", sender)
            .putOpt("senderName", senderName).putOpt("replyTo", replyTo).putOpt("request", request).putOpt("markdown", markdown)
            .apply {
                if (attachments.isNotEmpty()) put("attachments", JSONArray(attachments.map { it.toJson() }))
                if (previews.isNotEmpty()) put("previews", JSONArray(previews.map { it.toJson() }))
                music?.let { put("music", it.toJson()) }
                location?.let { put("location", it.toJson()) }
                if (once) put("once", true)
                if (system) put("system", true)
                if (description) put("description", true)
            }
    }

    data class Edit(
        val conversation: String,
        val id: String,
        val text: String,
        val time: Long,
        val markdown: String? = null,
        /** Replaces the message's previews when not null. */
        val previews: List<InboxPreview>? = null
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "edit").put("conversation", conversation).put("id", id)
            .put("text", text).put("time", time).putOpt("markdown", markdown)
            .apply { previews?.let { put("previews", JSONArray(it.map { preview -> preview.toJson() })) } }
    }

    data class Delete(val conversation: String, val id: String) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "delete").put("conversation", conversation).put("id", id)
    }

    /** [sender] reacted to [message] with [emoji], or took it back with [remove]. Null [sender] is the person. */
    data class Reaction(
        val conversation: String,
        val message: String,
        val emoji: String,
        val sender: String? = null,
        val remove: Boolean = false,
        val time: Long = System.currentTimeMillis()
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "reaction").put("conversation", conversation).put("message", message)
            .put("emoji", emoji).putOpt("sender", sender).put("time", time).apply { if (remove) put("remove", true) }
    }

    /** A live location of [message] moved, or stopped with [stop]. */
    data class LocationUpdate(
        val conversation: String,
        val message: String,
        val lat: Double,
        val lng: Double,
        val accuracy: Float? = null,
        val stop: Boolean = false,
        val time: Long = System.currentTimeMillis()
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "location_update").put("conversation", conversation).put("message", message)
            .put("lat", lat).put("lng", lng).putOpt("accuracy", accuracy?.toDouble()).put("time", time)
            .apply { if (stop) put("stop", true) }
    }

    /**
     * How far the person's own messages in [conversation] got: everyone else received those up to
     * [delivered] and read those up to [read]. Needs [Protocol.FEATURE_RECEIPTS].
     */
    data class Receipts(val conversation: String, val delivered: Long? = null, val read: Long? = null) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "receipts").put("conversation", conversation)
            .putOpt("delivered", delivered).putOpt("read", read)
    }

    /** The person read [conversation] in the app up to and including [time]. */
    data class Read(val conversation: String, val time: Long) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "read").put("conversation", conversation).put("time", time)
    }

    data class ConversationDelete(val id: String) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "conversation_delete").put("id", id)
    }

    /** The image [id] names, encoded by the library; null when it is not available. The library sends these itself. */
    class Image internal constructor(val id: String, val data: ByteArray?) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "image").put("id", id)
            .putOpt("data", data?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) })
    }

    /** A file the host asked for, shared through the library's provider. The library sends these itself. */
    class File internal constructor(
        val request: String,
        val uri: String?,
        val mime: String? = null,
        val name: String? = null,
        val size: Long? = null,
        val reason: String? = null
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "file").put("request", request).putOpt("uri", uri)
            .putOpt("mime", mime).putOpt("name", name).putOpt("size", size).putOpt("reason", reason)
    }

    data class ReplyFailed(val request: String, val reason: String? = null) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "reply_failed").put("request", request).putOpt("reason", reason)
    }
}
