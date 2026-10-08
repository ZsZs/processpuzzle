package com.processpuzzle.composition;

import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.workflow.definition.usecases.outbound.EventCatalogPort;
import org.springframework.stereotype.Component;

/**
 * Answers base-workflow's question whether a TRIGGERING_EVENT start event names a real event, from
 * base-event's catalog. Neither library names the other; this is where the knowledge that both are
 * deployed together lives — see the package documentation.
 */
@Component
public class EventCatalogAdapter implements EventCatalogPort {

    private final FindEventDefinition findEventDefinition;

    public EventCatalogAdapter(FindEventDefinition findEventDefinition) {
        this.findEventDefinition = findEventDefinition;
    }

    @Override
    public boolean exists(String orgKey, String eventDefinitionId) {
        return findEventDefinition.exists(orgKey, eventDefinitionId);
    }
}
