package com.processpuzzle.composition;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogParameter;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogStateChange;
import com.processpuzzle.workflow.definition.usecases.outbound.EventCatalogPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers base-workflow's questions about the events a workflow names — whether one exists, of which
 * kind, which state change a SYSTEM one stands for, and which parameters it carries — from base-event's catalog. Neither library names the other; this is where the
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

    @Override
    public Optional<CatalogStateChange> stateChangeOf(String orgKey, String eventDefinitionId) {
        return findEventDefinition.stateChangeOf(orgKey, eventDefinitionId)
                .map(change -> new CatalogStateChange(change.subjectType(), change.state()));
    }

    @Override
    public List<CatalogParameter> releasedParametersOf(String orgKey, String eventDefinitionId) {
        return parametersOf(orgKey, eventDefinitionId, Channel.RELEASED);
    }

    @Override
    public List<CatalogParameter> draftParametersOf(String orgKey, String eventDefinitionId) {
        return parametersOf(orgKey, eventDefinitionId, Channel.DRAFT);
    }

    private List<CatalogParameter> parametersOf(String orgKey, String eventDefinitionId, Channel channel) {
        return findEventDefinition.parametersOf(orgKey, eventDefinitionId, channel).stream()
                .map(parameter -> new CatalogParameter(parameter.name(), parameter.type(), parameter.entityType(),
                        parameter.required()))
                .toList();
    }
}
