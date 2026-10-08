package com.processpuzzle.workflow.definition.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One intermediate event of a {@link Workflow} — BPMN's intermediate throw or catch event. It names
 * an entry of the organization's event catalog by {@link #eventDefinitionId} and takes part in the
 * flow the way a {@link TaskUse} does: {@link #dependsOn} decides when it is reached, and a task —
 * or another event — may depend on it.
 *
 * <p>A {@link EventDirection#THROW THROW} publishes its event the moment it is reached. A
 * {@link EventDirection#CATCH CATCH} waits until its event occurs: a SYSTEM event is matched by
 * subject, a MESSAGE by {@link #correlationKey}'s value, a SIGNAL reaches every waiting instance.
 *
 * <p>{@link #id} shares one namespace with the workflow's task and start-event ids, because
 * {@code dependsOn} names all of them by id.
 *
 * <p>Not a JPA entity: {@link Workflow#getEvents()} stores the list in a JSONB column, as
 * {@link TaskUse} is stored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventUse {

    private String id;

    private String name;

    /** Id of an event of the organization's catalog — base-event's {@code EventDefinition}. */
    private String eventDefinitionId;

    private EventDirection direction;

    /**
     * Ids of task uses or events of this same workflow that must be done before this event is
     * reached. Empty means it is reached when the instance starts.
     */
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /** How {@link #dependsOn} is satisfied. Null is read as {@link JoinType#ALL}. */
    @Builder.Default
    private JoinType joinType = JoinType.ALL;

    /**
     * MESSAGE only, and required there: the context variable carrying the correlation value. A
     * throw sends its value; a catch waits for a message whose value equals its own.
     */
    private String correlationKey;

    /**
     * CATCH — context variable names to paths into the occurred event. THROW — payload attribute
     * names to paths into the instance context.
     */
    private Map<String, String> payloadMapping;

    // Ignored: Jackson would otherwise store them as "throw"/"catch" in the JSONB column and then fail to
    // read that column back.
    @JsonIgnore
    public boolean isThrow() {
        return direction == EventDirection.THROW;
    }

    @JsonIgnore
    public boolean isCatch() {
        return direction == EventDirection.CATCH;
    }
}
