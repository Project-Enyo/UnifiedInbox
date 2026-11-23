package com.github.projectenyo.unifiedinbox

/** Names from SPEC.md, shared by connectors and hosts. */
object Protocol {
    const val VERSION = 2
    private const val P = "com.github.projectenyo.unifiedinbox"

    const val ACTION_HOST_REGISTER = "$P.host.REGISTER"
    const val ACTION_HOST_UNREGISTER = "$P.host.UNREGISTER"
    const val ACTION_HOST_EVENT = "$P.host.EVENT"

    const val ACTION_REGISTERED = "$P.connector.REGISTERED"
    const val ACTION_REGISTRATION_FAILED = "$P.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "$P.connector.UNREGISTERED"
    const val ACTION_REPLY = "$P.connector.REPLY"
    const val ACTION_MARK_READ = "$P.connector.MARK_READ"
    const val ACTION_REACT = "$P.connector.REACT"
    const val ACTION_OPEN = "$P.connector.OPEN"
    const val ACTION_FETCH_IMAGE = "$P.connector.FETCH_IMAGE"
    const val ACTION_FETCH_FILE = "$P.connector.FETCH_FILE"

    const val LINK_URI = "unifiedinbox://link"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_PI = "pi"
    const val EXTRA_VERSION = "version"
    const val EXTRA_FEATURES = "features"
    const val EXTRA_LABEL = "label"
    const val EXTRA_REASON = "reason"
    const val EXTRA_EVENTS = "events"
    const val EXTRA_CONVERSATION = "conversation"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_TEXT = "text"
    const val EXTRA_REQUEST = "request"
    const val EXTRA_TIME = "time"
    const val EXTRA_REPLY_TO = "replyTo"
    const val EXTRA_ATTACHMENTS = "attachments"
    const val EXTRA_ATTACHMENT = "attachment"
    const val EXTRA_LOCATION = "location"
    const val EXTRA_EMOJI = "emoji"
    const val EXTRA_REMOVE = "remove"
    const val EXTRA_IMAGE = "image"

    const val FEATURE_REPLY = "reply"
    const val FEATURE_MARK_READ = "mark_read"
    const val FEATURE_OPEN = "open"
    const val FEATURE_IMAGES = "images"
    const val FEATURE_FILES = "files"
    const val FEATURE_REACT = "react"
    const val FEATURE_RECEIPTS = "receipts"
    const val FEATURE_REPLY_TO = "reply_to"
    const val FEATURE_SEND_ATTACHMENTS = "send_attachments"
    const val FEATURE_SEND_LOCATION = "send_location"

    const val REASON_DENIED = "DENIED"
    const val REASON_ACTION_REQUIRED = "ACTION_REQUIRED"
    const val REASON_VERSION = "VERSION"
    const val REASON_INTERNAL_ERROR = "INTERNAL_ERROR"

    /** Broadcasts stay well below Android's transaction limit; longer histories are split. */
    const val MAX_EVENTS_BYTES = 256 * 1024

    /** Images in `image` events: at most this many pixels on the longer side, and this many bytes before encoding. */
    const val MAX_IMAGE_PIXELS = 512
    const val MAX_IMAGE_BYTES = 96 * 1024
}
