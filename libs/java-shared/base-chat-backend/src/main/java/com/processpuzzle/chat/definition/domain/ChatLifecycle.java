package com.processpuzzle.chat.definition.domain;

/**
 * When a conversation of a definition exists, and when it may be written to.
 */
public enum ChatLifecycle {

    /** The conversation is created together with its subject. */
    AUTO_CREATE,

    /** The conversation is created the first time somebody with a create role opens it. */
    ON_DEMAND,

    /** Writable only while the subject is in one of the definition's writable states. */
    STATE_BOUND
}
