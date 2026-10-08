package com.processpuzzle.event.domain;

/**
 * What raises an event of an {@link EventDefinition}.
 *
 * <ul>
 *   <li>SYSTEM — the platform, when a fact matching the definition's binding happens.</li>
 *   <li>MESSAGE — a workflow, addressed to one correlated recipient.</li>
 *   <li>SIGNAL — a workflow, broadcast to every listener.</li>
 * </ul>
 *
 * <p>Only SYSTEM events are raised in this version; the other two can be catalogued already.
 */
public enum EventKind {
    SYSTEM, MESSAGE, SIGNAL
}
