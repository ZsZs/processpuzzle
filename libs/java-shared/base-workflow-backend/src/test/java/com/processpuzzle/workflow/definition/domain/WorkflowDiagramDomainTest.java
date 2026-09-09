package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDiagramDomainTest {

    private static final String ORG = "org-1";
    private static final String WORKFLOW_ID = "order-fulfillment-workflow";

    private static DiagramNodeLayout node(String nodeId, double x, double y) {
        return DiagramNodeLayout.builder().nodeId(nodeId).position(DiagramPoint.builder().x(x).y(y).build()).build();
    }

    /** A workflow opened but never arranged is the normal starting point, and the canvas appends to these. */
    @Test
    void startsWithEmptyNodeAndEdgeLists() {
        WorkflowDiagram diagram = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).build();

        assertThat(diagram.getNodes()).isEmpty();
        assertThat(diagram.getEdges()).isEmpty();
        assertThat(diagram.getViewport()).isNull();
    }

    @Test
    void replaceLayout_replacesEverythingAtOnce() {
        WorkflowDiagram diagram = WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(node("task:gone", 1, 1)))
                .build();

        diagram.replaceLayout(
                List.of(node("task:review-order", 10, 20)),
                List.of(DiagramEdgeLayout.builder().edgeId("task:a->task:b").build()),
                DiagramViewport.builder().x(-5).y(-6).scale(0.8).build());

        assertThat(diagram.getNodes()).extracting(DiagramNodeLayout::getNodeId).containsExactly("task:review-order");
        assertThat(diagram.getEdges()).hasSize(1);
        assertThat(diagram.getViewport().getScale()).isEqualTo(0.8);
    }

    /**
     * A {@code null} list is a *cleared* layout rather than an untouched one — which is what makes a save
     * prune the rows for nodes the workflow no longer has.
     */
    @Test
    void replaceLayout_readsNullAsCleared() {
        WorkflowDiagram diagram = WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(node("task:gone", 1, 1)))
                .edges(List.of(DiagramEdgeLayout.builder().edgeId("a->b").build()))
                .viewport(DiagramViewport.builder().scale(1).build())
                .build();

        diagram.replaceLayout(null, null, null);

        assertThat(diagram.getNodes()).isEmpty();
        assertThat(diagram.getEdges()).isEmpty();
        assertThat(diagram.getViewport()).isNull();
    }

    /** Copied in, so a caller that keeps its own list cannot go on editing what was persisted. */
    @Test
    void replaceLayout_copiesTheListsItIsGiven() {
        WorkflowDiagram diagram = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).build();
        List<DiagramNodeLayout> caller = new ArrayList<>(List.of(node("task:review-order", 10, 20)));

        diagram.replaceLayout(caller, List.of(), null);
        caller.clear();

        assertThat(diagram.getNodes()).hasSize(1);
    }

    /**
     * Identity is the ({@code orgKey}, {@code workflowId}) pair, matching {@link Workflow}'s own: two tenants
     * may both own {@code order-fulfillment-workflow}, with entirely different arrangements.
     */
    @Test
    void isIdentifiedByOrganizationAndWorkflow() {
        WorkflowDiagram mine = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).build();
        WorkflowDiagram theirs = WorkflowDiagram.builder().orgKey("org-2").workflowId(WORKFLOW_ID).build();
        WorkflowDiagram sameAgain = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).nodes(List.of(node("task:a", 5, 5))).build();

        assertThat(mine).isNotEqualTo(theirs);
        // Equal by identity, not by arrangement — the layout is what changes; the row it belongs to does not.
        assertThat(mine).isEqualTo(sameAgain);
    }

    @Test
    void keyEqualsAndHashesByBothParts() {
        assertThat(new WorkflowDiagramKey(ORG, WORKFLOW_ID))
                .isEqualTo(new WorkflowDiagramKey(ORG, WORKFLOW_ID))
                .hasSameHashCodeAs(new WorkflowDiagramKey(ORG, WORKFLOW_ID))
                .isNotEqualTo(new WorkflowDiagramKey("org-2", WORKFLOW_ID))
                .isNotEqualTo(new WorkflowDiagramKey(ORG, "other-workflow"));
        assertThat(new WorkflowDiagramKey(ORG, WORKFLOW_ID)).hasToString(ORG + "/" + WORKFLOW_ID);
    }

    // Required by JPA, and the setters with it: an @IdClass has to be instantiable through a no-arg ctor.
    @Test
    void keyIsSettableForJpa() {
        WorkflowDiagramKey key = new WorkflowDiagramKey();
        key.setOrgKey(ORG);
        key.setWorkflowId(WORKFLOW_ID);

        assertThat(key.getOrgKey()).isEqualTo(ORG);
        assertThat(key.getWorkflowId()).isEqualTo(WORKFLOW_ID);
    }
}
