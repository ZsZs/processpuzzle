package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.api.WorkflowInstancesApi;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.inbound.CancelWorkflowInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindAllWorkflowInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindWorkflowInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListArtifactInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import com.processpuzzle.workflow.model.CancelWorkflowInstanceRequest;
import com.processpuzzle.workflow.model.PageOfWorkflowInstance;
import com.processpuzzle.workflow.model.WorkflowInstance;
import com.processpuzzle.workflow.model.StartWorkflowRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@code WorkflowInstancesApi} (from the "Workflow Instances" tag).
 *
 * <p><b>Generator-uncertainty note:</b> {@code cancelWorkflowInstance} has an untitled inline
 * {@code {reason: string}} request body in base-workflow-api.yaml (not a {@code $ref}'d schema),
 * so the generated Java parameter type for it could not be confirmed without running
 * {@code mvn generate-sources}. This assumes it comes through as a raw {@code Object} — the
 * common openapi-generator "spring" default for an anonymous, untitled inline object schema — and
 * extracts {@code reason} defensively. If the real build generates a named class instead (e.g.
 * {@code CancelWorkflowInstanceRequest}), replace the {@code Object} parameter type and the
 * extraction logic below with a direct {@code .getReason()} call.
 */
@RestController
public class WorkflowInstancesEndpoint implements WorkflowInstancesApi {

    private final StartWorkflowInstanceUseCase startWorkflowInstance;
    private final FindWorkflowInstanceUseCase findWorkflowInstance;
    private final FindAllWorkflowInstancesUseCase findAllWorkflowInstances;
    private final CancelWorkflowInstanceUseCase cancelWorkflowInstance;
    private final ListTaskInstancesUseCase listTaskInstances;
    private final ListArtifactInstancesUseCase listArtifactInstances;
    private final WorkflowExecutionMapper mapper;

    public WorkflowInstancesEndpoint(StartWorkflowInstanceUseCase startWorkflowInstance,
                                     FindWorkflowInstanceUseCase findWorkflowInstance,
                                     FindAllWorkflowInstancesUseCase findAllWorkflowInstances,
                                     CancelWorkflowInstanceUseCase cancelWorkflowInstance,
                                     ListTaskInstancesUseCase listTaskInstances,
                                     ListArtifactInstancesUseCase listArtifactInstances,
                                     WorkflowExecutionMapper mapper) {
        this.startWorkflowInstance = startWorkflowInstance;
        this.findWorkflowInstance = findWorkflowInstance;
        this.findAllWorkflowInstances = findAllWorkflowInstances;
        this.cancelWorkflowInstance = cancelWorkflowInstance;
        this.listTaskInstances = listTaskInstances;
        this.listArtifactInstances = listArtifactInstances;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<WorkflowInstance> startWorkflowInstance(String orgKey, StartWorkflowRequest request) {
        Map<String, Object> context = request.getContext() == null ? Map.of() : request.getContext();
        var instance = startWorkflowInstance.start(orgKey, request.getWorkflowId(), request.getEntityId(), context);
        return new ResponseEntity<>(toFullModel(orgKey, instance.getId()), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<WorkflowInstance> getWorkflowInstance(String orgKey, String instanceId) {
        return ResponseEntity.ok(toFullModel(orgKey, UUID.fromString(instanceId)));
    }

    /**
     * Every entry of the page carries its task and artifact instances — the same shape
     * {@code getWorkflowInstance} returns. base-entity's generated form reads the record out of the list
     * its store already loaded rather than re-fetching it by id, so a summary projection here would
     * render an empty form whose save destroyed what the projection dropped; see the contract's note on
     * {@code listWorkflowInstances}.
     *
     * <p>The rows come from the page itself rather than from {@link #toFullModel}: re-reading each
     * instance by id would repeat a query the page has already answered, and would fail the whole list
     * with a 404 for a row deleted between the two reads.
     *
     * <p>The two child collections are still read per row, so this is N+1 — bounded, {@code size}
     * defaulting to 20 in the contract. If a caller ever pages much wider than that, the fix is a batch
     * read keyed by instance id, not a return to summaries.
     */
    @Override
    public ResponseEntity<PageOfWorkflowInstance> listWorkflowInstances(
            String orgKey, String workflowId, com.processpuzzle.workflow.model.WorkflowInstanceStatus status,
            String entityId, String where, String order, Integer page, Integer size) {
        WorkflowInstanceStatus domainStatus = status == null ? null : WorkflowInstanceStatus.valueOf(status.getValue());
        var query = new FindAllWorkflowInstancesUseCase.Query(orgKey, workflowId, domainStatus, entityId, where, order, page, size);
        var result = findAllWorkflowInstances.findAll(query);
        List<WorkflowInstance> content = result.getContent().stream()
                .map(instance -> mapper.toModel(instance,
                        listTaskInstances.findAll(orgKey, instance.getId()),
                        listArtifactInstances.findAll(orgKey, instance.getId())))
                .toList();
        return ResponseEntity.ok(mapper.toPageModel(result, content));
    }

    @Override
    public ResponseEntity<Void> cancelWorkflowInstance(String orgKey, String instanceId, CancelWorkflowInstanceRequest request) {
        String reason = request == null ? null : request.getReason();
        cancelWorkflowInstance.cancel(orgKey, UUID.fromString(instanceId), reason);
        return ResponseEntity.noContent().build();
    }

    private WorkflowInstance toFullModel(String orgKey, UUID instanceId) {
        var instance = findWorkflowInstance.findByOrgKeyAndId(orgKey, instanceId);
        var tasks = listTaskInstances.findAll(orgKey, instanceId);
        var artifacts = listArtifactInstances.findAll(orgKey, instanceId);
        return mapper.toModel(instance, tasks, artifacts);
    }
}
