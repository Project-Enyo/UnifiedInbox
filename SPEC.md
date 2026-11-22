# UnifiedInbox specification, Android, version 1

UnifiedInbox lets chat apps share their conversations with one inbox app the person chooses, in
both directions: the inbox shows every chat in one place, and replies, read state and opening a chat
go back through the app that owns it. It follows the model of UnifiedPush: a small set of intents,
no central server, and a connector library that apps embed.

The key words MUST, MUST NOT, SHOULD and MAY are used as in RFC 2119.

## Roles

- **Connector**: a chat app that owns conversations, such as a messenger. It decides which host
  receives its chats.
- **Host**: an inbox app that shows conversations from connectors, such as a launcher.

A connector is registered with at most one host at a time. A host serves any number of connectors.

## Names

Every action, extra and scheme below starts with the prefix

```
com.github.projectenyo.unifiedinbox
```

written as `P` below. `P.host.REGISTER` therefore means
`com.github.projectenyo.unifiedinbox.host.REGISTER`.

## Discovery

A host MUST declare an exported broadcast receiver that handles the actions `P.host.REGISTER`,
`P.host.UNREGISTER` and `P.host.EVENT`.

A host SHOULD declare an exported activity that handles `android.intent.action.VIEW` with the URI
`unifiedinbox://link`. When started for a result, it MUST finish with `RESULT_OK` and an extra `pi`
holding an immutable `PendingIntent` that the host created. The connector reads the host's package
from `PendingIntent.getCreatorPackage()`. This lets the person's default inbox be found without a list.

A connector finds hosts with `PackageManager.queryBroadcastReceivers(Intent(P.host.REGISTER))`.

A connector MUST declare an exported broadcast receiver that handles the actions
`P.connector.REGISTERED`, `P.connector.REGISTRATION_FAILED`, `P.connector.UNREGISTERED`,
`P.connector.REPLY` and `P.connector.MARK_READ`. A connector that supports opening conversations MUST
declare an exported activity that handles `P.connector.OPEN`.

## Trust

Every connector to host broadcast MUST be sent with an explicit package. On API 34 and newer the
connector MUST send it with `BroadcastOptions.setShareIdentityEnabled(true)`, and the host MUST take the
sender from `BroadcastReceiver.getSentFromPackage()`. On older versions the connector MUST add an extra
`pi`, an immutable `PendingIntent` it created, and the host MUST take the sender from
`PendingIntent.getCreatorPackage()`. A host MUST NOT trust a package name given in a plain string.

At registration the connector creates a random **token** of at least 128 bits. Every host to connector
broadcast and every `P.connector.OPEN` start MUST carry that token in the extra `token`, and the
connector MUST ignore anything with an unknown token. Every connector to host broadcast MUST also carry
it, and the host MUST ignore a token that is not registered for the sending package.

Hosts see every message of every conversation the connector shares. A connector SHOULD therefore
register only with hosts the person chose, and MAY limit itself to hosts it trusts. The reference
connector library trusts the Enyo launcher and apps installed in a `priv-app` directory of the system
image, and lets apps change that list.

## Registration

The connector sends `P.host.REGISTER` to the chosen host with:

| Extra | Type | Meaning |
| --- | --- | --- |
| `token` | String | The registration token. |
| `pi` | PendingIntent | Identity on API 33 and older, see Trust. |
| `version` | int | The protocol version the connector speaks, `1`. |
| `features` | String[] | What the connector supports: `reply`, `mark_read`, `open`. |
| `label` | String | Optional: a description of what is shared, such as an account name. |

The host answers with `P.connector.REGISTERED` and the extras `token` and `version` (the protocol
version the host speaks), or with `P.connector.REGISTRATION_FAILED` and the extras `token` and `reason`:

| Reason | Meaning |
| --- | --- |
| `DENIED` | The person refused this app in the host. |
| `ACTION_REQUIRED` | The host needs the person to do something first, such as turning sharing on. |
| `VERSION` | The host does not speak this protocol version. |
| `INTERNAL_ERROR` | Anything else. |

A host MAY ask the person before it answers. Registering again with a registered token MUST succeed
and MUST NOT duplicate data.

After `P.connector.REGISTERED` the connector SHOULD send its recent history (see Events) so the host
starts with the same view as the app.

Either side ends a registration: the connector sends `P.host.UNREGISTER` with `token`; the host sends
`P.connector.UNREGISTERED` with `token`. The host SHOULD then delete everything that connector shared.

## Events: connector to host

The connector sends `P.host.EVENT` with the extras `token` and `events`, a JSON array of event
objects as a String. A broadcast SHOULD stay below 256 KiB; longer histories are split into several
broadcasts. Events are applied in order. Unknown event types and unknown fields MUST be ignored.

Times are milliseconds since the Unix epoch. Ids are opaque strings, unique within the connector.

### `conversation`

Creates or updates a conversation.

```json
{"type": "conversation", "id": "c1", "title": "Climbing", "group": true, "shortcut": "chat:c1",
 "members": [{"id": "u7", "name": "Ada", "phone": "+4915112345678", "uri": "https://example.org/u/ada"}],
 "muted": false}
```

`shortcut` is optional: the conversation shortcut id the app sets on its notifications for this chat
(`Notification.getShortcutId()`). Hosts that also read notifications use it to recognize the same chat,
and SHOULD NOT store a connector's notifications as messages while it is registered. `title` is
optional for one to one chats. In a one to one chat `members` SHOULD list the other person
only. `phone` and `uri` are optional and help the host link people to the address book; a host MUST
NOT link people by name alone.

### `message`

Creates a message, or updates it if the id is known.

```json
{"type": "message", "conversation": "c1", "id": "m42", "sender": "u7", "senderName": "Ada",
 "text": "See you at six", "time": 1764000000000, "outgoing": false, "replyTo": "m40",
 "request": "r9"}
```

`sender` is a member id and is omitted for messages the person sent (`outgoing`: true). `text` is the
message as plain text; messages without text, such as a photo, SHOULD carry a short description like
`Photo`. `request` is set only on a message that answers a `P.connector.REPLY` with that request id.

### `edit`

```json
{"type": "edit", "conversation": "c1", "id": "m42", "text": "See you at seven", "time": 1764000100000}
```

### `delete`

```json
{"type": "delete", "conversation": "c1", "id": "m42"}
```

### `read`

The person read the conversation in the app up to and including `time`.

```json
{"type": "read", "conversation": "c1", "time": 1764000200000}
```

### `conversation_delete`

```json
{"type": "conversation_delete", "id": "c1"}
```

### `reply_failed`

A reply from the host could not be sent.

```json
{"type": "reply_failed", "request": "r9", "reason": "The conversation was left"}
```

## Actions: host to connector

Every action carries `token`. Connectors that did not announce a feature MAY ignore its action.

### `P.connector.REPLY` (feature `reply`)

Extras: `conversation`, `text`, `request` (a host chosen id). The connector sends `text` to the
conversation exactly as if the person had typed it in the app, including end to end encryption, and
then reports the sent message as a `message` event with `outgoing`: true and the same `request`, or
reports `reply_failed`.

### `P.connector.MARK_READ` (feature `mark_read`)

Extras: `conversation`, `time`. The connector marks the conversation read up to `time`, including
read receipts if the person enabled them in the app, and SHOULD NOT echo a `read` event for it.

### `P.connector.OPEN` (feature `open`)

The host starts the connector's activity with the action `P.connector.OPEN`, `FLAG_ACTIVITY_NEW_TASK`
and the extras `token` and `conversation`. The connector shows that conversation. The host SHOULD only
do this in response to the person, while the host is in the foreground.

## Versioning

This is version 1. Later versions add actions, events and fields; they keep the meaning of everything
here. A side that receives a higher `version` than it speaks continues with the features it knows.
