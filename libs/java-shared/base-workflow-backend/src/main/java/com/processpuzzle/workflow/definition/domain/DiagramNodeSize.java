package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Node dimensions in diagram coordinates, matching ng-diagram's own {@code Size}.
 *
 * <p>The generated model this maps to is called {@code NodeSize} rather than {@code Size} because a
 * schema of that name would shadow {@code jakarta.validation.constraints.Size} in the generated
 * model package. Here the name is prefixed for the reason given on {@link DiagramPoint}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramNodeSize {

    private double width;
    private double height;
}
