package com.processpuzzle.chat.conversation.usecases.outbound;

/**
 * What the host application knows about a subject that access decisions need. Either part may be null: not
 * every kind of subject has an owner or a state.
 *
 * @param owner the owner's subject in the identity provider
 * @param state the subject's current state, for STATE_BOUND chats
 */
public record SubjectInfo(String owner, String state) {
}
