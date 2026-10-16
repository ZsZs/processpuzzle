package com.processpuzzle.event.domain;

import com.processpuzzle.shared.event.EventParameterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One value an occurrence of an {@link EventDefinition} carries — base-event-api's {@code EventParameter}.
 *
 * <p>Stored in the definition's JSONB column, so a plain value object rather than an entity of its own.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EventParameter {

    private String name;

    private EventParameterType type;

    /** {@link EventParameterType#ENTITY_REF} only — the base-entity definition code, or {@code document}. */
    private String entityType;

    private boolean required;

    private String description;
}
