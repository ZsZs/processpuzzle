package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pan offset and zoom of the modeler canvas, persisted so that reopening a large workflow returns to
 * the part of it the user was working on rather than to whatever an automatic fit chooses.
 *
 * <p>One JSONB column rather than base-state's three scalar ones: this module already stores its
 * nested objects as JSON ({@link Workflow#getStartCondition()}), so a single column keeps a
 * half-specified viewport unrepresentable without three coordinated null checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramViewport {

    private double x;
    private double y;

    /** Zoom factor, {@code 1} being 100%. */
    private double scale;
}
