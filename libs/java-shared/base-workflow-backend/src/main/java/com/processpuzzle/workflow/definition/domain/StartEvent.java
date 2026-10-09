package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One way an instance of a {@link Workflow} may come into being — BPMN's start event. A workflow
 * lists any number of them and an explicit start is admitted when <em>any</em> one does (see
 * {@code StartEventAdmission}); a workflow with none can be started by anyone through
 * {@code /instances}. {@link #startType} selects the mechanism and decides which of the remaining
 * fields carry meaning; the others are ignored.
 *
 * <p>{@link #id} is unique within the workflow, and against the workflow's task ids too: a later
 * phase lets a task's {@code dependsOn} name a start event, so the two share one namespace.
 *
 * <p>One flat class with a discriminant field rather than a subtype per mechanism: that is how
 * every ProcessPuzzle contract models a variant, and it keeps this value a plain Jackson round-trip
 * in the JSONB column {@link Workflow#getStartEvents()} is stored in — a subtype tree would need
 * {@code @JsonTypeInfo} / {@code @JsonSubTypes} wiring to survive it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartEvent {

    private String id;

    private String name;

    private WorkflowStartConditionType startType;

    /** INPUT_ARTIFACT — the artifacts, and optionally the states, that must be present. */
    @Builder.Default
    private List<RequiredStartArtifact> requiredArtifacts = new ArrayList<>();

    /** TRIGGERING_EVENT — the event that starts the workflow. */
    private String eventType;

    /**
     * TRIGGERING_EVENT and INPUT_ARTIFACT — maps the event into the new instance's context: the
     * catalogued occurrence, or the platform fact that started it. Keys are context variable names,
     * values are JSONPath expressions into the event.
     */
    private Map<String, String> payloadMapping;

    /**
     * ROLE_DEFINITION — {@link RoleDefinition} ids allowed to start the workflow by hand. Empty or
     * null means any user may.
     */
    private List<String> authorizedRoles;

    /** TIME_BASED_PRECONDITION — the milestone whose arrival is the trigger. */
    private String milestoneRef;

    /** TIME_BASED_PRECONDITION — PPCL guard that must hold when the milestone arrives. Not evaluated yet. */
    private String preconditionExpression;

    /** TIME_BASED_PRECONDITION — when the workflow starts on its own: a DATE or a CYCLE literal. */
    private TimerDefinition timer;

    /**
     * Whether this mechanism can admit a start requested by hand through {@code /instances}.
     * TRIGGERING_EVENT and TIME_BASED_PRECONDITION fire on their own; an explicit start is not
     * what they describe.
     */
    public boolean admitsManualStart() {
        return startType == WorkflowStartConditionType.ROLE_DEFINITION
                || startType == WorkflowStartConditionType.INPUT_ARTIFACT;
    }
}
