package com.processpuzzle.chat.conversation.domain;

/**
 * Where a conversation is in its life. Only {@link #OPEN} accepts new messages; the other two are read-only.
 */
public enum ConversationStatus {

    /** Accepts messages from everyone the definition's write roles admit. */
    OPEN,

    /** Read-only, and may be reopened — a STATE_BOUND chat whose subject left its writable states. */
    CLOSED,

    /** Read-only for good — the subject is gone or its workflow instance completed; only retention removes it. */
    ARCHIVED
}
