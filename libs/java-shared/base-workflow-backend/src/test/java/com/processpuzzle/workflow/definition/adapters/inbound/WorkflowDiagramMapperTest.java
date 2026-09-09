package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.DiagramEdgeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeSize;
import com.processpuzzle.workflow.definition.domain.DiagramPoint;
import com.processpuzzle.workflow.definition.domain.DiagramViewport;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.model.WorkflowDiagramInput;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDiagramMapperTest {

    private static final String ORG = "org-1";
    private static final String WORKFLOW_ID = "order-fulfillment-workflow";

    private WorkflowDiagramMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WorkflowDiagramMapper();
    }

    // ---------------------------------------------------------------- to domain

    @Test
    void toDomain_mapsTheWholeLayout() {
        WorkflowDiagramInput input = new WorkflowDiagramInput(WORKFLOW_ID)
                .nodes(List.of(new com.processpuzzle.workflow.model.DiagramNodeLayout("task:review-order", new com.processpuzzle.workflow.model.Point(10.0, 20.0))
                        .size(new com.processpuzzle.workflow.model.NodeSize(170.0, 76.0))))
                .edges(List.of(new com.processpuzzle.workflow.model.DiagramEdgeLayout("task:a->task:b")
                        .points(List.of(new com.processpuzzle.workflow.model.Point(1.0, 2.0)))
                        .sourcePort("port-right")
                        .targetPort("port-left")
                        .routing("orthogonal")))
                .viewport(new com.processpuzzle.workflow.model.DiagramViewport(-5.0, -6.0, 0.8));

        WorkflowDiagram diagram = mapper.toDomain(ORG, WORKFLOW_ID, input);

        assertThat(diagram.getOrgKey()).isEqualTo(ORG);
        assertThat(diagram.getWorkflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(diagram.getNodes()).hasSize(1);
        assertThat(diagram.getNodes().get(0).getNodeId()).isEqualTo("task:review-order");
        assertThat(diagram.getNodes().get(0).getPosition().getX()).isEqualTo(10.0);
        assertThat(diagram.getNodes().get(0).getSize().getWidth()).isEqualTo(170.0);
        assertThat(diagram.getEdges().get(0).getPoints()).hasSize(1);
        assertThat(diagram.getEdges().get(0).getRouting()).isEqualTo("orthogonal");
        assertThat(diagram.getViewport().getScale()).isEqualTo(0.8);
    }

    /**
     * The version guard's client half: the caller's own version reaches the use case, which is what lets a
     * stale write be refused rather than silently landing.
     */
    @Test
    void toDomain_carriesTheVersionTheCallerLastRead() {
        WorkflowDiagram diagram = mapper.toDomain(ORG, WORKFLOW_ID, new WorkflowDiagramInput(WORKFLOW_ID).version(3L));

        assertThat(diagram.getVersion()).isEqualTo(3L);
    }

    /** Omitted means "overwrite unconditionally", which is what a first save necessarily does. */
    @Test
    void toDomain_leavesAnOmittedVersionNull() {
        assertThat(mapper.toDomain(ORG, WORKFLOW_ID, new WorkflowDiagramInput(WORKFLOW_ID)).getVersion()).isNull();
    }

    /**
     * The path is the source of truth; the body's own {@code workflowId} is informational. A diagram is
     * always addressed by a workflow that must already exist, so the body can only repeat or contradict it.
     */
    @Test
    void toDomain_takesTheIdentityFromThePathRatherThanTheBody() {
        WorkflowDiagram diagram = mapper.toDomain(ORG, WORKFLOW_ID, new WorkflowDiagramInput("some-other-workflow"));

        assertThat(diagram.getWorkflowId()).isEqualTo(WORKFLOW_ID);
    }

    /** A workflow opened but never arranged legitimately has neither list. */
    @Test
    void toDomain_readsAbsentListsAsEmptyOnes() {
        WorkflowDiagram diagram = mapper.toDomain(ORG, WORKFLOW_ID, new WorkflowDiagramInput(WORKFLOW_ID));

        assertThat(diagram.getNodes()).isEmpty();
        assertThat(diagram.getEdges()).isEmpty();
        assertThat(diagram.getViewport()).isNull();
    }

    /** An absent size means the frontend's automatic layout sized the node; it must not become a 0x0 box. */
    @Test
    void toDomain_leavesAnAbsentSizeNull() {
        WorkflowDiagramInput input = new WorkflowDiagramInput(WORKFLOW_ID)
                .nodes(List.of(new com.processpuzzle.workflow.model.DiagramNodeLayout("task:a", new com.processpuzzle.workflow.model.Point(0.0, 0.0))));

        assertThat(mapper.toDomain(ORG, WORKFLOW_ID, input).getNodes().get(0).getSize()).isNull();
    }

    // ---------------------------------------------------------------- to model

    @Test
    void toModel_mapsTheWholeLayoutBack() {
        WorkflowDiagram diagram = WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(DiagramNodeLayout.builder()
                        .nodeId("task:review-order")
                        .position(DiagramPoint.builder().x(10).y(20).build())
                        .size(DiagramNodeSize.builder().width(170).height(76).build())
                        .build()))
                .edges(List.of(DiagramEdgeLayout.builder()
                        .edgeId("task:a->task:b")
                        .points(List.of(DiagramPoint.builder().x(1).y(2).build()))
                        .sourcePort("port-right")
                        .build()))
                .viewport(DiagramViewport.builder().x(-5).y(-6).scale(0.8).build())
                .version(4L)
                .build();

        com.processpuzzle.workflow.model.WorkflowDiagram model = mapper.toModel(diagram);

        assertThat(model.getWorkflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(model.getOrgKey()).isEqualTo(ORG);
        assertThat(model.getVersion()).isEqualTo(4L);
        assertThat(model.getNodes().get(0).getPosition().getY()).isEqualTo(20.0);
        assertThat(model.getNodes().get(0).getSize().getHeight()).isEqualTo(76.0);
        assertThat(model.getEdges().get(0).getPoints()).hasSize(1);
        assertThat(model.getEdges().get(0).getSourcePort()).isEqualTo("port-right");
        assertThat(model.getViewport().getScale()).isEqualTo(0.8);
    }

    @Test
    void toModel_leavesAnAbsentSizeAndViewportNull() {
        WorkflowDiagram diagram = WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(DiagramNodeLayout.builder().nodeId("task:a").position(DiagramPoint.builder().build()).build()))
                .build();

        com.processpuzzle.workflow.model.WorkflowDiagram model = mapper.toModel(diagram);

        assertThat(model.getNodes().get(0).getSize()).isNull();
        assertThat(model.getViewport()).isNull();
    }

    /** An edge with no waypoints round-trips as an empty list rather than as a null the client must guard. */
    @Test
    void toModel_readsAnEdgeWithNoWaypointsAsAnEmptyList() {
        WorkflowDiagram diagram = WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .edges(List.of(DiagramEdgeLayout.builder().edgeId("task:a->task:b").points(null).build()))
                .build();

        assertThat(mapper.toModel(diagram).getEdges().get(0).getPoints()).isEmpty();
    }

    @Test
    void toModel_wrapsAPageInTheEnvelope() {
        WorkflowDiagram diagram = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).build();

        var page = mapper.toModel(new PageImpl<>(List.of(diagram)));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getNumber()).isZero();
    }

    @Test
    void roundTripsALayoutUnchanged() {
        WorkflowDiagramInput input = new WorkflowDiagramInput(WORKFLOW_ID)
                .nodes(List.of(new com.processpuzzle.workflow.model.DiagramNodeLayout("lane:clerk", new com.processpuzzle.workflow.model.Point(0.0, 0.0))
                        .size(new com.processpuzzle.workflow.model.NodeSize(600.0, 108.0))))
                .edges(List.of(new com.processpuzzle.workflow.model.DiagramEdgeLayout("task:a->task:b").sourcePort("port-right")))
                .viewport(new com.processpuzzle.workflow.model.DiagramViewport(1.0, 2.0, 1.5));

        com.processpuzzle.workflow.model.WorkflowDiagram model = mapper.toModel(mapper.toDomain(ORG, WORKFLOW_ID, input));

        assertThat(model.getNodes()).isEqualTo(input.getNodes());
        assertThat(model.getViewport()).isEqualTo(input.getViewport());
        assertThat(model.getEdges().get(0).getSourcePort()).isEqualTo("port-right");
    }
}
