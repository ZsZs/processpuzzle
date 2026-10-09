package com.processpuzzle.workflow.definition.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * When a timer fires — of a timer catch {@link EventUse}, or of a TIME_BASED_PRECONDITION
 * {@link StartEvent}. {@link #expression} is an ISO 8601 literal of the form {@link #type} asks for, or a
 * {@code $.variable} path into the instance context, read when the timer is reached; see
 * {@link TimerExpressions}.
 *
 * <p>Not a JPA entity: stored inside the JSONB column of the event that carries it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimerDefinition {

    private TimerType type;

    private String expression;

    /** Whether {@link #expression} is a context path rather than a literal. */
    @JsonIgnore
    public boolean isPath() {
        return TimerExpressions.isPath(expression);
    }
}
