package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.DiagramEdgeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeSize;
import com.processpuzzle.workflow.definition.domain.DiagramPoint;
import com.processpuzzle.workflow.definition.domain.DiagramViewport;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteWorkflowDiagramUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllWorkflowDiagramsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindWorkflowDiagramUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.SaveWorkflowDiagramUseCase;
import com.processpuzzle.workflow.model.WorkflowDiagramInput;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The endpoint's own two responsibilities: the {@code 201}/{@code 200} split of the upsert, and passing the
 * path's {@code orgKey}/{@code workflowId} rather than the body's. Everything else it does is
 * {@link WorkflowDiagramMapper}'s, which {@link WorkflowDiagramMapperTest} covers.
 */
class WorkflowDiagramsEndpointTest {

    private static final String ORG = "org-1";
    private static final String WORKFLOW_ID = "order-fulfillment-workflow";

    private SaveWorkflowDiagramUseCase saveUseCase;
    private FindWorkflowDiagramUseCase findUseCase;
    private FindAllWorkflowDiagramsUseCase findAllUseCase;
    private DeleteWorkflowDiagramUseCase deleteUseCase;
    private WorkflowDiagramsEndpoint endpoint;

    @BeforeEach
    void setUp() {
        saveUseCase = mock(SaveWorkflowDiagramUseCase.class);
        findUseCase = mock(FindWorkflowDiagramUseCase.class);
        findAllUseCase = mock(FindAllWorkflowDiagramsUseCase.class);
        deleteUseCase = mock(DeleteWorkflowDiagramUseCase.class);
        endpoint = new WorkflowDiagramsEndpoint(saveUseCase, findUseCase, findAllUseCase, deleteUseCase, new WorkflowDiagramMapper());
    }

    private static WorkflowDiagram diagram() {
        return WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(DiagramNodeLayout.builder()
                        .nodeId("task:review-order")
                        .position(DiagramPoint.builder().x(10).y(20).build())
                        .size(DiagramNodeSize.builder().width(170).height(76).build())
                        .build()))
                .edges(List.of(DiagramEdgeLayout.builder().edgeId("task:a->task:b").sourcePort("port-right").build()))
                .viewport(DiagramViewport.builder().x(-5).y(-6).scale(0.8).build())
                .build();
    }

    @Test
    void getWorkflowDiagram_answersTheArrangement() {
        when(findUseCase.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(diagram());

        ResponseEntity<com.processpuzzle.workflow.model.WorkflowDiagram> response = endpoint.getWorkflowDiagram(ORG, WORKFLOW_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getNodes()).hasSize(1);
        assertThat(response.getBody().getNodes().get(0).getNodeId()).isEqualTo("task:review-order");
    }

    /** 201 the first time this workflow is arranged. */
    @Test
    void saveWorkflowDiagram_answers201OnFirstArrangement() {
        when(saveUseCase.save(any(WorkflowDiagram.class))).thenReturn(new SaveWorkflowDiagramUseCase.Result(diagram(), true));

        ResponseEntity<com.processpuzzle.workflow.model.WorkflowDiagram> response =
                endpoint.saveWorkflowDiagram(ORG, WORKFLOW_ID, new WorkflowDiagramInput(WORKFLOW_ID));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    /** 200 every time after — which is why the use case reports whether it inserted. */
    @Test
    void saveWorkflowDiagram_answers200OnAReplace() {
        when(saveUseCase.save(any(WorkflowDiagram.class))).thenReturn(new SaveWorkflowDiagramUseCase.Result(diagram(), false));

        ResponseEntity<com.processpuzzle.workflow.model.WorkflowDiagram> response =
                endpoint.saveWorkflowDiagram(ORG, WORKFLOW_ID, new WorkflowDiagramInput(WORKFLOW_ID));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    /**
     * The path is the source of truth. A body naming a different workflow can only ever repeat or contradict
     * the URL, and the URL is what was authorized.
     */
    @Test
    void saveWorkflowDiagram_ignoresTheWorkflowIdInTheBody() {
        when(saveUseCase.save(any(WorkflowDiagram.class))).thenReturn(new SaveWorkflowDiagramUseCase.Result(diagram(), false));

        endpoint.saveWorkflowDiagram(ORG, WORKFLOW_ID, new WorkflowDiagramInput("some-other-workflow"));

        verify(saveUseCase).save(org.mockito.ArgumentMatchers.argThat(saved ->
                WORKFLOW_ID.equals(saved.getWorkflowId()) && ORG.equals(saved.getOrgKey())));
    }

    @Test
    void listWorkflowDiagrams_answersAPageEnvelope() {
        when(findAllUseCase.findAll(eq(ORG), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(diagram())));

        var response = endpoint.listWorkflowDiagrams(ORG, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
    }

    @Test
    void deleteWorkflowDiagram_answers204() {
        ResponseEntity<Void> response = endpoint.deleteWorkflowDiagram(ORG, WORKFLOW_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(deleteUseCase).delete(ORG, WORKFLOW_ID);
    }
}
