package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where one modeler node sits on the canvas.
 *
 * <p>Keyed by the <em>diagram</em> node id rather than by a domain key, which is the one place this
 * layer departs from base-state's otherwise identical {@code NodeLayout}: a node here stands for a
 * task, a lane, an artifact or a tool, and those ids are only unique within their own catalog — so
 * the frontend prefixes them ({@code task:<id>}, {@code lane:<roleId>}) and the prefixed form is
 * what identifies a row. Opaque to this module, which stores and returns it without interpretation.
 *
 * <p>{@code nodeId} is deliberately <em>not</em> validated against the workflow: see
 * {@code SaveWorkflowDiagramUseCase} for why a row naming a node nothing renders any more is
 * tolerated rather than rejected.
 *
 * <p>A {@code null} {@link #size} means the node keeps whatever size the frontend's automatic layout
 * computed for it — which for a lane is the band that holds its tasks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramNodeLayout {

    private String nodeId;
    private DiagramPoint position;
    private DiagramNodeSize size;
}
