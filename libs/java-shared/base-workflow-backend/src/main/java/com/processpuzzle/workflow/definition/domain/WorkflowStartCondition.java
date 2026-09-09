package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How an instance of a {@link Workflow} comes into being. {@link #startType} selects the mechanism
 * and decides which of the remaining fields carry meaning; the others are ignored. A workflow
 * without a start condition can only be started explicitly through {@code /instances}.
 *
 * <p>One flat class with a discriminant field rather than a subtype per mechanism: that is how
 * every ProcessPuzzle contract models a variant, and it keeps this value a plain Jackson round-trip
 * in the JSONB column {@link Workflow#getStartCondition()} is stored in — a subtype tree would need
 * {@code @JsonTypeInfo} / {@code @JsonSubTypes} wiring to survive it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStartCondition {

    private WorkflowStartConditionType startType;

    /** INPUT_ARTIFACT — the artifacts, and optionally the states, that must be present. */
    @Builder.Default
    private List<RequiredStartArtifact> requiredArtifacts = new ArrayList<>();

    /** TRIGGERING_EVENT — the event that starts the workflow. */
    private String eventType;

    /**
     * TRIGGERING_EVENT — maps the event payload into the new instance's context. Keys are context
     * variable names, values are JSONPath expressions into the event.
     */
    private Map<String, String> payloadMapping;

    /**
     * ROLE_DEFINITION — {@link RoleDefinition} ids allowed to start the workflow by hand. Empty or
     * null means any user may.
     */
    private List<String> authorizedRoles;

    /** TIME_BASED_PRECONDITION — the milestone whose arrival is the trigger. */
    private String milestoneRef;

    /** TIME_BASED_PRECONDITION — PPCL guard that must hold when the milestone arrives. */
    private String preconditionExpression;
}
