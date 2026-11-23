# UnifiedInbox

[![JitPack](https://jitpack.io/v/Project-Enyo/UnifiedInbox.svg)](https://jitpack.io/#Project-Enyo/UnifiedInbox)

UnifiedInbox is UnifiedPush for chats. Chat apps share their conversations with one inbox app the
person chooses, and the inbox can reply, mark chats read and open them through the app that owns
them. There is no server: apps talk through a few Android intents described in [SPEC.md](SPEC.md).

- **Connector**: a chat app, such as a messenger, that shares its conversations.
- **Host**: an inbox app, such as the [Enyo launcher](https://github.com/Project-Enyo/enyo-launcher), that shows them.

This repository holds the specification and the connector library for chat apps.

## Add the connector library

The library is built by [JitPack](https://jitpack.io/#Project-Enyo/UnifiedInbox).

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.Project-Enyo:UnifiedInbox:1.1.0")
}
```

## Use it

Set a handler once, in `Application.onCreate`, because the host can wake the app:

```kotlin
UnifiedInbox.setHandler(object : InboxHandler {
    override fun onRegistered(context: Context, host: String) {
        // Share recent conversations so the host starts with the same view as the app.
    }
    override fun onReply(context: Context, conversation: String, text: String, request: String) {
        // Send like the app's own composer, then share the sent message with request = request.
    }
    override fun onMarkRead(context: Context, conversation: String, time: Long) { /* mark read */ }
    override fun openIntent(context: Context, conversation: String) =
        Intent(context, ChatActivity::class.java).putExtra("conversation", conversation)
})
```

Choose a host and register, for example from the app's settings:

```kotlin
UnifiedInbox.tryUseDefaultHost(activity) { found ->
    if (found) UnifiedInbox.register(activity, setOf(Protocol.FEATURE_REPLY, Protocol.FEATURE_MARK_READ, Protocol.FEATURE_OPEN))
}
// Or list them yourself: UnifiedInbox.hosts(context), then UnifiedInbox.useHost(context, packageName).
```

Share what happens in the app:

```kotlin
UnifiedInbox.send(context,
    InboxEvent.Conversation("c1", title = null, members = listOf(InboxMember("u7", "Ada", avatar = ada.pictureId)), shortcut = "chat:c1"),
    InboxEvent.Message("c1", id = "m42", text = "See you at six", markdown = "See you at **six**", time = sentAt, sender = "u7", senderName = "Ada")
)
UnifiedInbox.send(context, InboxEvent.Read("c1", readUpTo))
```

Messages can carry everything a chat app shows: `attachments` (photos, videos, GIFs, stickers, voice
messages, audio and files), link `previews`, `music`, a `location` that may be live, view `once`
messages and `system` notices. `InboxEvent.Reaction`, `Edit`, `Delete` and `LocationUpdate` change
earlier messages.

Pictures, thumbnails and preview images are named by image ids that change when the image does; files
are fetched when the person opens them. The host asks, and the library scales, encodes and shares what
your handler returns:

```kotlin
// Announce Protocol.FEATURE_IMAGES and Protocol.FEATURE_FILES when registering.
override fun image(context: Context, id: String): Bitmap? = loadImage(id)
override fun file(context: Context, conversation: String, message: String, attachment: String): InboxFile? =
    decryptAttachment(conversation, message, attachment)?.let { InboxFile(it, mime, name) }
```

The host can also react (`FEATURE_REACT`, `onReact`) and send replies that quote a message, files or a
place (`FEATURE_REPLY_TO`, `FEATURE_SEND_ATTACHMENTS`, `FEATURE_SEND_LOCATION`, `onSend`).

A host sees every message the app shares. By default the library only accepts the Enyo launcher and
apps installed in a `priv-app` directory of the system image as hosts; set `UnifiedInbox.isTrusted`
to change that.

## Write a host

Hosts implement the receiver and the optional link activity from [SPEC.md](SPEC.md). The names are
in `Protocol`. Enyo's implementation is a working example.
