package com.processpuzzle.chat.conversation.domain;

/**
 * What the caller may do with one conversation, worked out from the definition's roles, the subject's
 * state and the conversation's status at the moment of asking. Kept apart so a caller can tell "not allowed"
 * (403) from "not possible right now" (409), and neither from "not yours to know" (404).
 *
 * @param canRead   may see the conversation at all
 * @param mayWrite  holds a role that allows posting, whatever the state of the subject or the conversation
 * @param mayCreate holds a role that allows opening a conversation of this definition on the subject
 * @param writable  the conversation is open and, for a STATE_BOUND chat, the subject is in a writable state
 */
public record ConversationAccess(boolean canRead, boolean mayWrite, boolean mayCreate, boolean writable) {

    /** May post right now. */
    public boolean canWrite() {
        return canRead && mayWrite && writable;
    }
}
