package com.processpuzzle.composition;

import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.workflow.definition.usecases.outbound.EventCatalogPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers base-workflow's questions about the events a workflow names — whether one exists, and of
 * which kind — from base-event's catalog. Neither library names the other; this is where the
 * knowledge that both are deployed together lives — see the package documentation.
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

    @Override
    public Optional<CatalogEventKind> kindOf(String orgKey, String eventDefinitionId) {
        return findEventDefinition.kindOf(orgKey, eventDefinitionId);
    }
}
