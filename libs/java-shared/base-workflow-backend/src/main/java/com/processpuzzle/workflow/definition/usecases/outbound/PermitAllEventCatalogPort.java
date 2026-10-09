package com.processpuzzle.workflow.definition.usecases.outbound;

/**
 * Accepts every event name, used when the deploying application supplies no {@link EventCatalogPort}
 * — a deployment without base-event, where a triggering event can only be checked by its firing.
 *
 * <p>Deliberately not a {@code @Component}: {@code WorkflowValidator} instantiates it as a fallback via
 * {@code ObjectProvider#getIfUnique}, the same pattern as {@code PermitAllStartAuthorizationPort}.
 */
public class PermitAllEventCatalogPort implements EventCatalogPort {

    @Override
    public boolean exists(String orgKey, String eventDefinitionId) {
        return true;
    }
}
