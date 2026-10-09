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
 * <p>base-workflow mirrors these as {@code CatalogEventKind} in {@code shared.event}; the names must
 * stay equal.
 */
public enum EventKind {
    SYSTEM, MESSAGE, SIGNAL
}
