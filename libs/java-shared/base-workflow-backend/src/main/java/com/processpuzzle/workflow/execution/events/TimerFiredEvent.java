package com.processpuzzle.workflow.execution.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published every time a timer catch fires — a CYCLE once per firing. Nothing in this module observes
 * it; it is the hook for a reminder or an escalation notice.
 *
 * @param fireCount how many times the timer has fired, this firing included
 * @param dueAt     when the timer was due
 * @param firedAt   when the sweep fired it
 */
public record TimerFiredEvent(String orgKey, UUID workflowInstanceId, UUID eventInstanceId, String eventUseId,
                              int fireCount, Instant dueAt, Instant firedAt) {
}
