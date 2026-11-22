package com.github.projectenyo.unifiedinbox

import org.json.JSONArray
import org.json.JSONObject

/** A person in a conversation. [phone] and [uri] help the host link them to the address book. */
data class InboxMember(val id: String, val name: String? = null, val phone: String? = null, val uri: String? = null)

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
        val shortcut: String? = null
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "conversation").put("id", id).putOpt("title", title).putOpt("shortcut", shortcut)
            .put("group", group).put("muted", muted)
            .put("members", JSONArray(members.map {
                JSONObject().put("id", it.id).putOpt("name", it.name).putOpt("phone", it.phone).putOpt("uri", it.uri)
            }))
    }

    data class Message(
        val conversation: String,
        val id: String,
        val text: String,
        val time: Long,
        val outgoing: Boolean = false,
        /** The member id of the sender; null for the person's own messages. */
        val sender: String? = null,
        val senderName: String? = null,
        val replyTo: String? = null,
        /** The request id of the host reply this message answers. */
        val request: String? = null
    ) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "message").put("conversation", conversation).put("id", id)
            .put("text", text).put("time", time).put("outgoing", outgoing).putOpt("sender", sender)
            .putOpt("senderName", senderName).putOpt("replyTo", replyTo).putOpt("request", request)
    }

    data class Edit(val conversation: String, val id: String, val text: String, val time: Long) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "edit").put("conversation", conversation).put("id", id)
            .put("text", text).put("time", time)
    }

    data class Delete(val conversation: String, val id: String) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "delete").put("conversation", conversation).put("id", id)
    }

    /** The person read [conversation] in the app up to and including [time]. */
    data class Read(val conversation: String, val time: Long) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "read").put("conversation", conversation).put("time", time)
    }

    data class ConversationDelete(val id: String) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "conversation_delete").put("id", id)
    }

    data class ReplyFailed(val request: String, val reason: String? = null) : InboxEvent() {
        override fun toJson() = JSONObject().put("type", "reply_failed").put("request", request).putOpt("reason", reason)
    }
}
