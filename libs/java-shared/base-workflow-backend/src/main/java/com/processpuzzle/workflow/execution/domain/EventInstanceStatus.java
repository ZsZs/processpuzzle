package com.processpuzzle.workflow.execution.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Run-time state of an {@link EventInstance}.
 *
 * <ul>
 *   <li>PENDING — not reached yet.</li>
 *   <li>WAITING — a catch that was reached and waits for its event.</li>
 *   <li>OCCURRED — a catch whose event arrived.</li>
 *   <li>THROWN — a throw that was reached and raised.</li>
 *   <li>CANCELLED — withdrawn: the instance was cancelled, or every dependent moved on without it.</li>
 * </ul>
 */
public enum EventInstanceStatus {
    PENDING, WAITING, OCCURRED, THROWN, CANCELLED;

    private static final Set<EventInstanceStatus> DONE = EnumSet.of(OCCURRED, THROWN);
    private static final Set<EventInstanceStatus> TERMINAL = EnumSet.of(OCCURRED, THROWN, CANCELLED);

    /** Whether a task or event depending on this one may go ahead. */
    public boolean isDone() {
        return DONE.contains(this);
    }

    /** Whether the event will not change any more. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
