package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.domain.EventParameter;
import com.processpuzzle.event.model.EventDefinitionInput;
import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.event.usecase.ImportOutcome;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Between the generated {@code com.processpuzzle.event.model} DTOs and the domain. */
@Component
public class EventDefinitionMapper {

    public EventDefinition toDomain(EventDefinitionInput input) {
        return EventDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .kind(input.getKind() == null ? null : EventKind.valueOf(input.getKind().name()))
                .subjectType(input.getSubjectType())
                .action(input.getAction())
                .state(input.getState())
                .parameters(toDomain(input.getParameters()))
                .version(input.getVersion())
                .build();
    }

    private static List<EventParameter> toDomain(List<com.processpuzzle.event.model.EventParameter> parameters) {
        List<EventParameter> domain = new ArrayList<>();
        if (parameters != null) {
            parameters.forEach(parameter -> domain.add(EventParameter.builder()
                    .name(parameter.getName())
                    .type(parameter.getType())
                    .entityType(parameter.getEntityType())
                    .required(Boolean.TRUE.equals(parameter.getRequired()))
                    .description(parameter.getDescription())
                    .build()));
        }
        return domain;
    }

    private static List<com.processpuzzle.event.model.EventParameter> toModel(List<EventParameter> parameters) {
        return parameters.stream()
                .map(parameter -> new com.processpuzzle.event.model.EventParameter()
                        .name(parameter.getName())
                        .type(parameter.getType())
                        .entityType(parameter.getEntityType())
                        .required(parameter.isRequired())
                        .description(parameter.getDescription()))
                .toList();
    }

    public com.processpuzzle.event.model.EventDefinition toModel(EventDefinition definition) {
        return new com.processpuzzle.event.model.EventDefinition()
                .id(definition.getId())
                .name(definition.getName())
                .description(definition.getDescription())
                .kind(definition.getKind() == null
                        ? null : com.processpuzzle.event.model.EventKind.valueOf(definition.getKind().name()))
                .subjectType(definition.getSubjectType())
                .action(definition.getAction())
                .state(definition.getState())
                .parameters(toModel(definition.getParameters()))
                .version(definition.getVersion())
                .createdAt(toOffsetDateTime(definition.getCreatedAt()))
                .updatedAt(toOffsetDateTime(definition.getUpdatedAt()));
    }

    public ImportResult toImportResult(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
