package com.processpuzzle.shared.event;

/**
 * What raises an event of the catalog — base-event's {@code EventKind}, mirrored here so that a
 * subscriber can tell how an occurrence is delivered without compiling against base-event.
 *
 * <ul>
 *   <li>SYSTEM — the platform, when a fact matching the definition's binding happens; a catch
 *       waiting for it is matched by subject.</li>
 *   <li>MESSAGE — a workflow, addressed to exactly one recipient matched on its correlation value.</li>
 *   <li>SIGNAL — a workflow, broadcast to every listener.</li>
 * </ul>
 */
public enum CatalogEventKind {
    SYSTEM, MESSAGE, SIGNAL
}
