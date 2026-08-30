package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.api.ProcessInstancesApi;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.inbound.CancelProcessInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindAllProcessInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindProcessInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListArtifactInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.StartProcessInstanceUseCase;
import com.processpuzzle.workflow.model.CancelProcessInstanceRequest;
import com.processpuzzle.workflow.model.PageOfProcessInstance;
import com.processpuzzle.workflow.model.ProcessInstance;
import com.processpuzzle.workflow.model.StartProcessRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@code ProcessInstancesApi} (from the "Process Instances" tag).
 *
 * <p><b>Generator-uncertainty note:</b> {@code cancelProcessInstance} has an untitled inline
 * {@code {reason: string}} request body in base-workflow-api.yaml (not a {@code $ref}'d schema),
 * so the generated Java parameter type for it could not be confirmed without running
 * {@code mvn generate-sources}. This assumes it comes through as a raw {@code Object} — the
 * common openapi-generator "spring" default for an anonymous, untitled inline object schema — and
 * extracts {@code reason} defensively. If the real build generates a named class instead (e.g.
 * {@code CancelProcessInstanceRequest}), replace the {@code Object} parameter type and the
 * extraction logic below with a direct {@code .getReason()} call.
 */
@RestController
public class ProcessInstancesEndpoint implements ProcessInstancesApi {

    private final StartProcessInstanceUseCase startProcessInstance;
    private final FindProcessInstanceUseCase findProcessInstance;
    private final FindAllProcessInstancesUseCase findAllProcessInstances;
    private final CancelProcessInstanceUseCase cancelProcessInstance;
    private final ListTaskInstancesUseCase listTaskInstances;
    private final ListArtifactInstancesUseCase listArtifactInstances;
    private final WorkflowExecutionMapper mapper;

    public ProcessInstancesEndpoint(StartProcessInstanceUseCase startProcessInstance,
                                     FindProcessInstanceUseCase findProcessInstance,
                                     FindAllProcessInstancesUseCase findAllProcessInstances,
                                     CancelProcessInstanceUseCase cancelProcessInstance,
                                     ListTaskInstancesUseCase listTaskInstances,
                                     ListArtifactInstancesUseCase listArtifactInstances,
                                     WorkflowExecutionMapper mapper) {
        this.startProcessInstance = startProcessInstance;
        this.findProcessInstance = findProcessInstance;
        this.findAllProcessInstances = findAllProcessInstances;
        this.cancelProcessInstance = cancelProcessInstance;
        this.listTaskInstances = listTaskInstances;
        this.listArtifactInstances = listArtifactInstances;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<ProcessInstance> startProcessInstance(String orgKey, StartProcessRequest request) {
        Map<String, Object> context = request.getContext() == null ? Map.of() : request.getContext();
        var instance = startProcessInstance.start(orgKey, request.getProcessDefinitionId(), request.getEntityId(), context);
        return new ResponseEntity<>(toFullModel(orgKey, instance.getId()), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ProcessInstance> getProcessInstance(String orgKey, String instanceId) {
        return ResponseEntity.ok(toFullModel(orgKey, UUID.fromString(instanceId)));
    }

    /**
     * Every entry of the page carries its task and artifact instances — the same shape
     * {@code getProcessInstance} returns. base-entity's generated form reads the record out of the list
     * its store already loaded rather than re-fetching it by id, so a summary projection here would
     * render an empty form whose save destroyed what the projection dropped; see the contract's note on
     * {@code listProcessInstances}.
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
    public ResponseEntity<PageOfProcessInstance> listProcessInstances(
            String orgKey, String processId, com.processpuzzle.workflow.model.ProcessInstanceStatus status,
            String entityId, String where, String order, Integer page, Integer size) {
        ProcessInstanceStatus domainStatus = status == null ? null : ProcessInstanceStatus.valueOf(status.getValue());
        var query = new FindAllProcessInstancesUseCase.Query(orgKey, processId, domainStatus, entityId, where, order, page, size);
        var result = findAllProcessInstances.findAll(query);
        List<ProcessInstance> content = result.getContent().stream()
                .map(instance -> mapper.toModel(instance,
                        listTaskInstances.findAll(orgKey, instance.getId()),
                        listArtifactInstances.findAll(orgKey, instance.getId())))
                .toList();
        return ResponseEntity.ok(mapper.toPageModel(result, content));
    }

    @Override
    public ResponseEntity<Void> cancelProcessInstance(String orgKey, String instanceId, CancelProcessInstanceRequest request) {
        String reason = request == null ? null : request.getReason();
        cancelProcessInstance.cancel(orgKey, UUID.fromString(instanceId), reason);
        return ResponseEntity.noContent().build();
    }

    private ProcessInstance toFullModel(String orgKey, UUID instanceId) {
        var instance = findProcessInstance.findByOrgKeyAndId(orgKey, instanceId);
        var tasks = listTaskInstances.findAll(orgKey, instanceId);
        var artifacts = listArtifactInstances.findAll(orgKey, instanceId);
        return mapper.toModel(instance, tasks, artifacts);
    }
}
