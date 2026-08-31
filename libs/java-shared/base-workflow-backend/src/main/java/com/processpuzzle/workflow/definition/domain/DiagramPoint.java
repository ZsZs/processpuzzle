package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A point in diagram coordinates, matching ng-diagram's own {@code Point}.
 *
 * <p>A getter/setter POJO rather than a record, and {@code Diagram}-prefixed rather than plainly
 * {@code Point}: the first because {@link WorkflowDiagram} stores the layout as JSONB columns, so
 * Jackson has to be able to instantiate this without relying on {@code -parameters} being on the
 * compiler command line — the same reason {@link TaskUse} is one. The second because this package
 * already holds some thirty classes, and an unqualified {@code Point} there says nothing about
 * which of them it belongs to.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramPoint {

    private double x;
    private double y;
}
