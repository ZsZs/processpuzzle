package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.api.WorkflowDiagramsApi;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteWorkflowDiagramUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllWorkflowDiagramsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindWorkflowDiagramUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.SaveWorkflowDiagramUseCase;
import com.processpuzzle.workflow.model.PageOfWorkflowDiagram;
import com.processpuzzle.workflow.model.WorkflowDiagramInput;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin adapter over the diagram use cases, same discipline as {@link WorkflowsEndpoint}: it validates
 * nothing and computes nothing itself, only translating between the generated shapes (via
 * {@link WorkflowDiagramMapper}) and use-case calls.
 *
 * <p>A separate controller from {@link WorkflowsEndpoint} because the diagram operations are tagged
 * {@code Workflow Diagrams} in the contract and therefore land on their own generated interface. That
 * separation is load-bearing rather than cosmetic: two {@code @RestController}s implementing the same
 * generated interface would register the same request mappings twice and fail the application context
 * at startup.
 *
 * <p>{@code orgKey}/path-vs-JWT verification is the application's security filter chain's job, exactly
 * as for {@link WorkflowsEndpoint} — nothing here re-checks it.
 */
@RestController
public class WorkflowDiagramsEndpoint implements WorkflowDiagramsApi {

    private final SaveWorkflowDiagramUseCase saveWorkflowDiagram;
    private final FindWorkflowDiagramUseCase findWorkflowDiagram;
    private final FindAllWorkflowDiagramsUseCase findAllWorkflowDiagrams;
    private final DeleteWorkflowDiagramUseCase deleteWorkflowDiagram;
    private final WorkflowDiagramMapper mapper;

    public WorkflowDiagramsEndpoint(SaveWorkflowDiagramUseCase saveWorkflowDiagram,
                                    FindWorkflowDiagramUseCase findWorkflowDiagram,
                                    FindAllWorkflowDiagramsUseCase findAllWorkflowDiagrams,
                                    DeleteWorkflowDiagramUseCase deleteWorkflowDiagram,
                                    WorkflowDiagramMapper mapper) {
        this.saveWorkflowDiagram = saveWorkflowDiagram;
        this.findWorkflowDiagram = findWorkflowDiagram;
        this.findAllWorkflowDiagrams = findAllWorkflowDiagrams;
        this.deleteWorkflowDiagram = deleteWorkflowDiagram;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PageOfWorkflowDiagram> listWorkflowDiagrams(
            String orgKey, String where, String order, Integer page, Integer size) {
        Page<WorkflowDiagram> result = findAllWorkflowDiagrams.findAll(orgKey, where, order, page, size);
        return ResponseEntity.ok(mapper.toModel(result));
    }

    @Override
    public ResponseEntity<com.processpuzzle.workflow.model.WorkflowDiagram> getWorkflowDiagram(
            String orgKey, String workflowId) {
        return ResponseEntity.ok(mapper.toModel(findWorkflowDiagram.findByOrgKeyAndWorkflowId(orgKey, workflowId)));
    }

    /** {@code 201} the first time this workflow is arranged, {@code 200} every time after. */
    @Override
    public ResponseEntity<com.processpuzzle.workflow.model.WorkflowDiagram> saveWorkflowDiagram(
            String orgKey, String workflowId, WorkflowDiagramInput input) {
        SaveWorkflowDiagramUseCase.Result result =
                saveWorkflowDiagram.save(mapper.toDomain(orgKey, workflowId, input));
        return ResponseEntity.status(result.created() ? 201 : 200).body(mapper.toModel(result.diagram()));
    }

    @Override
    public ResponseEntity<Void> deleteWorkflowDiagram(String orgKey, String workflowId) {
        deleteWorkflowDiagram.delete(orgKey, workflowId);
        return ResponseEntity.noContent().build();
    }
}
