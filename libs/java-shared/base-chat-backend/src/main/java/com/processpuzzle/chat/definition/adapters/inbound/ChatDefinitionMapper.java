package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.Multiplicity;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.model.ChatDefinitionInput;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Between the generated {@code com.processpuzzle.chat.model} DTOs and the domain. The enums are separate
 * vocabularies on each side and are translated by name.
 */
@Component
public class ChatDefinitionMapper {

    public ChatDefinition toDomain(ChatDefinitionInput input) {
        return ChatDefinition.builder()
                .name(input.getName())
                .description(input.getDescription())
                .subjectType(input.getSubjectType() == null ? null : SubjectType.valueOf(input.getSubjectType().name()))
                .subjectDefinition(input.getSubjectDefinition())
                .multiplicity(input.getMultiplicity() == null
                        ? Multiplicity.SINGLE : Multiplicity.valueOf(input.getMultiplicity().name()))
                .lifecycle(input.getLifecycle() == null
                        ? ChatLifecycle.ON_DEMAND : ChatLifecycle.valueOf(input.getLifecycle().name()))
                .writableStates(copy(input.getWritableStates()))
                .readRoles(copy(input.getReadRoles()))
                .writeRoles(copy(input.getWriteRoles()))
                .createRoles(copy(input.getCreateRoles()))
                .includeSubjectOwner(Boolean.TRUE.equals(input.getIncludeSubjectOwner()))
                .retentionDays(input.getRetentionDays())
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.chat.model.ChatDefinition toModel(ChatDefinition definition) {
        return new com.processpuzzle.chat.model.ChatDefinition()
                .key(definition.getDefinitionKey())
                .name(definition.getName())
                .description(definition.getDescription())
                .subjectType(com.processpuzzle.chat.model.ChatSubjectType.valueOf(definition.getSubjectType().name()))
                .subjectDefinition(definition.getSubjectDefinition())
                .multiplicity(com.processpuzzle.chat.model.ChatMultiplicity.valueOf(definition.getMultiplicity().name()))
                .lifecycle(com.processpuzzle.chat.model.ChatLifecycle.valueOf(definition.getLifecycle().name()))
                .writableStates(new ArrayList<>(definition.getWritableStates()))
                .readRoles(new ArrayList<>(definition.getReadRoles()))
                .writeRoles(new ArrayList<>(definition.getWriteRoles()))
                .createRoles(new ArrayList<>(definition.getCreateRoles()))
                .includeSubjectOwner(definition.isIncludeSubjectOwner())
                .retentionDays(definition.getRetentionDays())
                .version(definition.getVersion())
                .createdAt(offset(definition.getCreatedAt()))
                .updatedAt(offset(definition.getUpdatedAt()));
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
