# UnifiedInbox

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
    implementation("com.github.Project-Enyo.UnifiedInbox:connector:1.0.0")
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
    InboxEvent.Conversation("c1", title = null, members = listOf(InboxMember("u7", "Ada")), shortcut = "chat:c1"),
    InboxEvent.Message("c1", id = "m42", text = "See you at six", time = sentAt, sender = "u7", senderName = "Ada")
)
UnifiedInbox.send(context, InboxEvent.Read("c1", readUpTo))
```

A host sees every message the app shares. By default the library only accepts the Enyo launcher and
apps installed in a `priv-app` directory of the system image as hosts; set `UnifiedInbox.isTrusted`
to change that.

## Write a host

Hosts implement the receiver and the optional link activity from [SPEC.md](SPEC.md). The names are
in `Protocol`. Enyo's implementation is a working example.
