# UnifiedInbox specification, Android, version 2

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
`P.connector.REPLY`, `P.connector.MARK_READ`, `P.connector.REACT`, `P.connector.FETCH_IMAGE` and
`P.connector.FETCH_FILE`. A connector that supports opening conversations MUST
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
| `version` | int | The protocol version the connector speaks, `2`. |
| `features` | String[] | What the connector supports, from the features named under Actions. |
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
Everything a host receives is untrusted input.

### Images

Pictures of people and groups, thumbnails, stickers and link preview images are named by **image
ids**: opaque strings that MUST change whenever the image behind them changes. With the feature
`images`, the host fetches an image with `P.connector.FETCH_IMAGE` and SHOULD keep it per id. Images
are small enough to travel in an event; full size media are files, see Attachments.

### `conversation`

Creates or updates a conversation.

```json
{"type": "conversation", "id": "c1", "title": "Climbing", "group": true, "shortcut": "chat:c1", "avatar": "g12",
 "members": [{"id": "u7", "name": "Ada", "phone": "+4915112345678", "uri": "https://example.org/u/ada",
              "avatar": "a3f9"}],
 "muted": false}
```

`shortcut` is optional: the conversation shortcut id the app sets on its notifications for this chat
(`Notification.getShortcutId()`). Hosts that also read notifications use it to recognize the same chat,
and SHOULD NOT store a connector's notifications as messages while it is registered. `title` is
optional for one to one chats. In a one to one chat `members` SHOULD list the other person
only. `phone` and `uri` are optional and help the host link people to the address book; a host MUST
NOT link people by name alone.

A member's `avatar` is the image id of their picture, omitted when they have none. A group MAY carry
its own picture in the conversation's `avatar`; one to one chats SHOULD omit it, and the host shows the
other member's picture. When a connector with the feature `images` omits an `avatar`, the host SHOULD
stop showing the earlier picture.

### `message`

Creates a message, or updates it if the id is known.

```json
{"type": "message", "conversation": "c1", "id": "m42", "sender": "u7", "senderName": "Ada",
 "text": "See you at six", "markdown": "See you at **six**", "time": 1764000000000, "outgoing": false,
 "replyTo": "m40", "request": "r9"}
```

`sender` is a member id and is omitted for messages the person sent (`outgoing`: true). `text` is the
message as plain text; messages without text, such as a photo, SHOULD carry a short description like
`Photo`, so hosts that know nothing else still show something useful. `request` is set only on a
message that answers a `P.connector.REPLY` with that request id. `replyTo` is the id of the message
this one quotes.

These optional fields describe the message completely; a host shows what it understands and falls
back to `text`:

| Field | Meaning |
| --- | --- |
| `markdown` | The text with formatting, in CommonMark. `\|\|text\|\|` marks a spoiler the host hides until tapped. |
| `attachments` | Files in the message, see Attachments. |
| `previews` | Link previews: objects with `url`, and optionally `title`, `description`, `site` and `image` (an image id). |
| `music` | A song: `artist`, `title`, and optionally `url` and `image` (an image id). |
| `location` | A place: `lat`, `lng`, and optionally `accuracy` (meters), `name`, `live` (true while it keeps updating) and `until` (when live sharing ends). |
| `once` | `true` for a view once message. The host MUST NOT show its text, attachments or previews; it shows that there is such a message and opens it in the app. |
| `system` | `true` for a notice in the chat rather than a message, such as someone joining. |
| `description` | `true` when `text` only describes the other fields, such as `Photo` for a picture without a caption. Hosts that show those fields SHOULD NOT show `text` as well. |

Hosts MUST only open `url` values with the schemes `http` and `https`.

### Attachments

```json
{"id": "a1", "kind": "image", "name": "beach.jpg", "mime": "image/jpeg", "size": 482113,
 "width": 1600, "height": 1200, "thumbnail": "t-m42-a1", "spoiler": false}
```

`id` is unique within its message. `kind` is one of `image`, `video`, `gif`, `sticker`, `voice`,
`audio` and `file`; hosts treat unknown kinds as `file`. `name`, `mime`, `size` (bytes), `width`,
`height`, `duration` (milliseconds), `waveform` (an array of levels from 0 to 1, for voice messages),
`thumbnail` (an image id) and `spoiler` (hide until tapped) are optional. With the feature `files`, the
host fetches the content with `P.connector.FETCH_FILE`.

### `edit`

```json
{"type": "edit", "conversation": "c1", "id": "m42", "text": "See you at seven", "time": 1764000100000}
```

An edit MAY also carry `markdown` and `previews`, which replace the message's.

### `delete`

```json
{"type": "delete", "conversation": "c1", "id": "m42"}
```

### `reaction`

```json
{"type": "reaction", "conversation": "c1", "message": "m42", "sender": "u7", "emoji": "👍", "time": 1764000300000}
```

Adds the reaction `emoji` from `sender` to the message, or removes it when `remove` is `true`. `sender`
is omitted for the person's own reactions.

### `location_update`

```json
{"type": "location_update", "conversation": "c1", "message": "m50", "lat": 52.52, "lng": 13.405,
 "accuracy": 12, "time": 1764000400000}
```

Moves the live location of the message `message`. With `stop`: true, live sharing ended.

### `receipts` (feature `receipts`)

How far the person's own messages got: everyone else in the conversation received those sent up to
and including `delivered`, and read those up to and including `read`.

```json
{"type": "receipts", "conversation": "c1", "delivered": 1764000500000, "read": 1764000400000}
```

Either field may be omitted when it is unknown. Times only move forward; a host keeps the latest. A
host shows the person's messages as received or read from these, and as sent otherwise. People who
turned off read receipts in the app are only ever counted as having received a message.

### `read`

The person read the conversation in the app up to and including `time`.

```json
{"type": "read", "conversation": "c1", "time": 1764000200000}
```

### `conversation_delete`

```json
{"type": "conversation_delete", "id": "c1"}
```

### `image`

The answer to `P.connector.FETCH_IMAGE`.

```json
{"type": "image", "id": "a3f9", "data": "UklGRk..."}
```

`data` is a PNG, JPEG or WebP image of at most 512 pixels on its longer side and at most 96 KiB,
encoded as Base64 (RFC 4648) without line breaks, and omitted when the image is not available. A host
MUST ignore images it cannot decode or that break these limits. Pictures of people and groups SHOULD be
square; hosts crop them to the middle.

### `file`

The answer to `P.connector.FETCH_FILE`.

```json
{"type": "file", "request": "f3", "uri": "content://org.example.unifiedinbox.files/f3", "mime": "video/mp4",
 "name": "clip.mp4", "size": 7340032}
```

`uri` is a `content` URI the connector granted the host read access to with
`Context.grantUriPermission`. It is omitted, with an optional `reason`, when the file is not available.
The grant may end when the connector's process ends, so the host SHOULD read the file right away.

### `reply_failed`

Something the host sent could not be sent.

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

These optional extras need further features:

| Extra | Feature | Meaning |
| --- | --- | --- |
| `replyTo` | `reply_to` | The id of the message this reply quotes. |
| `attachments` | `send_attachments` | An `ArrayList<Uri>` of files to send, with `text` as their caption, which may be empty. The host grants the connector read access to each with `Context.grantUriPermission` first. |
| `location` | `send_location` | A JSON object with `lat`, `lng` and optionally `name`: a place to send. |

A reply with attachments may be reported as sent only once its files are uploaded.

### `P.connector.MARK_READ` (feature `mark_read`)

Extras: `conversation`, `time`. The connector marks the conversation read up to `time`, including
read receipts if the person enabled them in the app, and SHOULD NOT echo a `read` event for it.

### `P.connector.REACT` (feature `react`)

Extras: `conversation`, `message`, `emoji`, and `remove` (boolean) to take the person's reaction
back. The connector reacts as the person would in the app and reports the result as a `reaction`
event.

### `P.connector.OPEN` (feature `open`)

The host starts the connector's activity with the action `P.connector.OPEN`, `FLAG_ACTIVITY_NEW_TASK`
and the extras `token` and `conversation`. The connector shows that conversation. The host SHOULD only
do this in response to the person, while the host is in the foreground.

### `P.connector.FETCH_IMAGE` (feature `images`)

Extras: `image`, an image id. The connector answers with an `image` event. It SHOULD only serve images
of what it has shared, and MAY answer without `data` for any other id.

### `P.connector.FETCH_FILE` (feature `files`)

Extras: `conversation`, `message`, `attachment` (the attachment's `id`) and `request` (a host chosen
id). The connector makes the file available, decrypting or downloading it as needed, and answers with a
`file` event. The host SHOULD only fetch files in response to the person, or small files it shows
inline, such as voice messages.

## Versioning

This is version 2. Version 2 added images and the feature `images`; message formatting,
attachments, previews, music, locations, view once and system messages; the events `reaction`,
`location_update`, `receipts`, `image` and `file`; the features `files`, `react`, `receipts`, `reply_to`,
`send_attachments` and `send_location` with their actions and extras. Later versions add actions, events and fields; they
keep the meaning of everything here. A side that receives a higher `version` than it speaks continues
with the features it knows.
