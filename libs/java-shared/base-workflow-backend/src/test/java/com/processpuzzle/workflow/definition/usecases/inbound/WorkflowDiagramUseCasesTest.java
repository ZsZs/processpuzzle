package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.DiagramEdgeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramNodeLayout;
import com.processpuzzle.workflow.definition.domain.DiagramPoint;
import com.processpuzzle.workflow.definition.domain.DiagramViewport;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowDiagramUseCasesTest {

    private static final String ORG = "org-1";
    private static final String WORKFLOW_ID = "order-fulfillment-workflow";

    private WorkflowDiagramRepository diagramRepo;
    private WorkflowRepository workflowRepo;

    @BeforeEach
    void setUp() {
        diagramRepo = mock(WorkflowDiagramRepository.class);
        workflowRepo = mock(WorkflowRepository.class);
    }

    private static WorkflowDiagram diagram() {
        return WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(DiagramNodeLayout.builder()
                        .nodeId("task:review-order")
                        .position(DiagramPoint.builder().x(10).y(20).build())
                        .build()))
                .edges(List.of(DiagramEdgeLayout.builder().edgeId("task:a->task:b").build()))
                .viewport(DiagramViewport.builder().x(-5).y(-6).scale(0.8).build())
                .build();
    }

    // ---------------------------------------------------------------- save

    /**
     * The upsert's first half. Nothing is server-assigned — the client always knows workflowId — so this
     * needs no round trip and has no first-save race.
     */
    @Test
    void saveWorkflowDiagram_insertsWhenNeverArranged() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.empty());
        when(diagramRepo.save(any(WorkflowDiagram.class))).thenAnswer(call -> call.getArgument(0));

        SaveWorkflowDiagramUseCase.Result result = useCase.save(diagram());

        assertThat(result.created()).isTrue();
        assertThat(result.diagram().getNodes()).hasSize(1);
    }

    /** The second half, and what makes {@code created} worth returning: 201 once, 200 every time after. */
    @Test
    void saveWorkflowDiagram_replacesWhenAlreadyArranged() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        WorkflowDiagram existing = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).build();
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(existing));
        when(diagramRepo.save(any(WorkflowDiagram.class))).thenAnswer(call -> call.getArgument(0));

        SaveWorkflowDiagramUseCase.Result result = useCase.save(diagram());

        assertThat(result.created()).isFalse();
        // Replaced in place, so the loaded row's @Version is what the flush locks on.
        assertThat(result.diagram()).isSameAs(existing);
        assertThat(existing.getNodes()).hasSize(1);
        assertThat(existing.getEdges()).hasSize(1);
        assertThat(existing.getViewport().getScale()).isEqualTo(0.8);
    }

    /**
     * The client-version guard base-workflow uses everywhere: two modelers who both opened version 3 must not
     * both be able to save over it.
     */
    @Test
    void saveWorkflowDiagram_refusesAStaleWrite() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        WorkflowDiagram stored = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).version(5L).build();
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(stored));
        WorkflowDiagram stale = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).version(3L).build();

        assertThatThrownBy(() -> useCase.save(stale))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("concurrently");
        verify(diagramRepo, never()).save(any());
    }

    @Test
    void saveWorkflowDiagram_acceptsTheCurrentVersion() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        WorkflowDiagram stored = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).version(5L).build();
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(stored));
        when(diagramRepo.save(any(WorkflowDiagram.class))).thenAnswer(call -> call.getArgument(0));
        WorkflowDiagram current = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).version(5L).build();

        assertThat(useCase.save(current).created()).isFalse();
    }

    /**
     * Omitting the version overwrites unconditionally, and it has to: the first save of a workflow has no
     * stored version to have read, and the modeler's "arrange" gesture cannot be asked to know that.
     */
    @Test
    void saveWorkflowDiagram_overwritesWhenNoVersionIsSupplied() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        WorkflowDiagram stored = WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).version(5L).build();
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(stored));
        when(diagramRepo.save(any(WorkflowDiagram.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(useCase.save(diagram()).created()).isFalse();
    }

    /** A layout for a workflow that does not exist has nothing to lay out, and would be unreachable anyway. */
    @Test
    void saveWorkflowDiagram_refusesAnUnknownWorkflow() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.save(diagram()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(WORKFLOW_ID);
        verify(diagramRepo, never()).save(any());
    }

    /**
     * The tolerance the contract promises. updateWorkflow is free to drop a task; rejecting a row that names
     * a node nothing renders any more would make two independent, individually-valid saves fail depending
     * only on the order they arrive in.
     */
    @Test
    void saveWorkflowDiagram_toleratesARowNamingANodeTheWorkflowNoLongerHas() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.empty());
        when(diagramRepo.save(any(WorkflowDiagram.class))).thenAnswer(call -> call.getArgument(0));
        WorkflowDiagram stale = WorkflowDiagram.builder()
                .orgKey(ORG)
                .workflowId(WORKFLOW_ID)
                .nodes(List.of(DiagramNodeLayout.builder().nodeId("task:deleted-since").position(DiagramPoint.builder().build()).build()))
                .build();

        assertThat(useCase.save(stale).diagram().getNodes()).hasSize(1);
    }

    /** An absent list is a *cleared* layout rather than an untouched one — which is what prunes stale rows. */
    @Test
    void saveWorkflowDiagram_readsAnAbsentListAsCleared() {
        SaveWorkflowDiagramUseCase useCase = new SaveWorkflowDiagramUseCase(diagramRepo, workflowRepo);
        WorkflowDiagram existing = diagram();
        when(workflowRepo.existsByOrgKeyAndId(ORG, WORKFLOW_ID)).thenReturn(true);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(existing));
        when(diagramRepo.save(any(WorkflowDiagram.class))).thenAnswer(call -> call.getArgument(0));

        useCase.save(WorkflowDiagram.builder().orgKey(ORG).workflowId(WORKFLOW_ID).nodes(null).edges(null).build());

        assertThat(existing.getNodes()).isEmpty();
        assertThat(existing.getEdges()).isEmpty();
        assertThat(existing.getViewport()).isNull();
    }

    // ---------------------------------------------------------------- find

    @Test
    void findWorkflowDiagram_readsTheArrangement() {
        FindWorkflowDiagramUseCase useCase = new FindWorkflowDiagramUseCase(diagramRepo);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(diagram()));

        assertThat(useCase.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID).getNodes()).hasSize(1);
    }

    /**
     * Throws rather than answering an empty layout: the modeler acts on the difference — it keeps its
     * automatic swimlane layout — so "never arranged" has to stay distinguishable from "arranged, then
     * emptied".
     */
    @Test
    void findWorkflowDiagram_reportsNeverArrangedAsNotFound() {
        FindWorkflowDiagramUseCase useCase = new FindWorkflowDiagramUseCase(diagramRepo);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(WORKFLOW_ID);
    }

    // ---------------------------------------------------------------- find all

    /**
     * The tenant specification is ANDed first and is never optional — RSQL permits a top-level OR, which
     * would otherwise let {@code where} escape the org filter.
     */
    @Test
    void findAllWorkflowDiagrams_scopesByOrganizationAndPages() {
        FindAllWorkflowDiagramsUseCase useCase = new FindAllWorkflowDiagramsUseCase(diagramRepo);
        Page<WorkflowDiagram> page = new PageImpl<>(List.of(diagram()));
        when(diagramRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        assertThat(useCase.findAll(ORG, null, null, null, null).getContent()).hasSize(1);
    }

    // ---------------------------------------------------------------- delete

    @Test
    void deleteWorkflowDiagram_discardsTheArrangement() {
        DeleteWorkflowDiagramUseCase useCase = new DeleteWorkflowDiagramUseCase(diagramRepo);
        WorkflowDiagram existing = diagram();
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.of(existing));

        useCase.delete(ORG, WORKFLOW_ID);

        verify(diagramRepo).delete(existing);
    }

    /**
     * A 404 rather than a silent success: this endpoint exists for the "re-arrange from scratch" gesture,
     * and the caller asked to discard something that was not there.
     */
    @Test
    void deleteWorkflowDiagram_reportsNothingToDiscard() {
        DeleteWorkflowDiagramUseCase useCase = new DeleteWorkflowDiagramUseCase(diagramRepo);
        when(diagramRepo.findByOrgKeyAndWorkflowId(ORG, WORKFLOW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.delete(ORG, WORKFLOW_ID)).isInstanceOf(NotFoundException.class);
    }
}
