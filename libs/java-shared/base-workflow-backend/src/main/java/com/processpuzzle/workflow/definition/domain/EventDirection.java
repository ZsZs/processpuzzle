package com.processpuzzle.workflow.definition.domain;

/**
 * Whether an {@link EventUse} raises its event or waits for it.
 *
 * <ul>
 *   <li>THROW — reaching the event publishes it, and the flow moves on at once.</li>
 *   <li>CATCH — reaching the event makes the flow wait until the event occurs.</li>
 * </ul>
 */
public enum EventDirection {
    THROW, CATCH
}
