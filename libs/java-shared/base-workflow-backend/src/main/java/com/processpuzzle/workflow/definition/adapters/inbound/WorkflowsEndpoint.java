package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.api.WorkflowsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ExportWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllWorkflowsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportWorkflowsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceWorkflowUseCase;
import com.processpuzzle.workflow.model.PageOfWorkflow;
import com.processpuzzle.workflow.model.Workflow;
import com.processpuzzle.workflow.model.WorkflowInput;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Implements the generated {@code WorkflowsApi} (from the "Workflow Definitions" tag).
 * See {@code WorkflowDefinitionMapper}'s Javadoc for the one field ({@code extends}) whose
 * generated accessor name couldn't be verified without running the code generator.
 */
@RestController
public class WorkflowsEndpoint implements WorkflowsApi {

    private final CreateWorkflowUseCase createWorkflow;
    private final ReplaceWorkflowUseCase replaceWorkflow;
    private final DeleteWorkflowUseCase deleteWorkflow;
    private final FindWorkflowUseCase findWorkflow;
    private final FindAllWorkflowsUseCase findAllWorkflows;
    private final ImportWorkflowsUseCase importWorkflows;
    private final ExportWorkflowUseCase exportWorkflow;
    private final WorkflowDefinitionMapper mapper;

    public WorkflowsEndpoint(CreateWorkflowUseCase createWorkflow,
                                       ReplaceWorkflowUseCase replaceWorkflow,
                                       DeleteWorkflowUseCase deleteWorkflow,
                                       FindWorkflowUseCase findWorkflow,
                                       FindAllWorkflowsUseCase findAllWorkflows,
                                       ImportWorkflowsUseCase importWorkflows,
                                       ExportWorkflowUseCase exportWorkflow,
                                       WorkflowDefinitionMapper mapper) {
        this.createWorkflow = createWorkflow;
        this.replaceWorkflow = replaceWorkflow;
        this.deleteWorkflow = deleteWorkflow;
        this.findWorkflow = findWorkflow;
        this.findAllWorkflows = findAllWorkflows;
        this.importWorkflows = importWorkflows;
        this.exportWorkflow = exportWorkflow;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Workflow> createWorkflow(String orgKey, WorkflowInput input) {
        var created = createWorkflow.create(orgKey, mapper.toDomain(orgKey, input));
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Workflow> getWorkflow(String orgKey, String workflowId) {
        return ResponseEntity.ok(mapper.toModel(findWorkflow.findByOrgKeyAndId(orgKey, workflowId)));
    }

    @Override
    public ResponseEntity<Workflow> updateWorkflow(String orgKey, String workflowId, WorkflowInput input) {
        var updated = replaceWorkflow.replace(orgKey, workflowId, mapper.toDomain(orgKey, input));
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteWorkflow(String orgKey, String workflowId) {
        deleteWorkflow.delete(orgKey, workflowId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PageOfWorkflow> listWorkflows(
            String orgKey, String where, String order, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toModel(findAllWorkflows.findAll(orgKey, where, order, page, size)));
    }

    @Override
    public ResponseEntity<ImportResult> importWorkflows(String orgKey, MultipartFile file) {
        try {
            ImportOutcome outcome = importWorkflows.execute(orgKey, file.getInputStream());
            return ResponseEntity.ok(mapper.toModel(outcome));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportWorkflow(String orgKey, String workflowId) {
        try {
            byte[] yaml = exportWorkflow.execute(orgKey, workflowId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + workflowId + "-export.yaml\"")
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(new ByteArrayResource(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
