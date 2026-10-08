package com.processpuzzle.workflow.definition.usecases.outbound;

/**
 * Whether an event definition exists in the organization's event catalog — asked when a workflow
 * whose TRIGGERING_EVENT start event names one is saved, so a typo is refused then rather than
 * surfacing as a workflow that silently never starts.
 *
 * <p>The catalog is base-event's, which this module does not depend on: the host application
 * supplies the adapter. Without one, {@code WorkflowValidator} falls back to
 * {@link PermitAllEventCatalogPort}.
 */
public interface EventCatalogPort {

    boolean exists(String orgKey, String eventDefinitionId);
}
