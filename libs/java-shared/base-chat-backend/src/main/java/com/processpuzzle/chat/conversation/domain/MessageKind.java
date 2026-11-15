package com.processpuzzle.chat.conversation.domain;

/**
 * What a message is. Text is the only kind there is today; system notices and call events are reserved for
 * later, which is why the kind is stored rather than implied.
 */
public enum MessageKind {

    /** Plain text typed by a participant. */
    TEXT
}
