package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.api.ProcessInstancesApi;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.inbound.CancelProcessInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindAllProcessInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindProcessInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListWorkProductInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.StartProcessInstanceUseCase;
import com.processpuzzle.workflow.model.CancelProcessInstanceRequest;
import com.processpuzzle.workflow.model.PageOfProcessInstanceSummary;
import com.processpuzzle.workflow.model.ProcessInstance;
import com.processpuzzle.workflow.model.StartProcessRequest;
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
    private final ListWorkProductInstancesUseCase listWorkProductInstances;
    private final WorkflowExecutionMapper mapper;

    public ProcessInstancesEndpoint(StartProcessInstanceUseCase startProcessInstance,
                                     FindProcessInstanceUseCase findProcessInstance,
                                     FindAllProcessInstancesUseCase findAllProcessInstances,
                                     CancelProcessInstanceUseCase cancelProcessInstance,
                                     ListTaskInstancesUseCase listTaskInstances,
                                     ListWorkProductInstancesUseCase listWorkProductInstances,
                                     WorkflowExecutionMapper mapper) {
        this.startProcessInstance = startProcessInstance;
        this.findProcessInstance = findProcessInstance;
        this.findAllProcessInstances = findAllProcessInstances;
        this.cancelProcessInstance = cancelProcessInstance;
        this.listTaskInstances = listTaskInstances;
        this.listWorkProductInstances = listWorkProductInstances;
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

    @Override
    public ResponseEntity<PageOfProcessInstanceSummary> listProcessInstances(
            String orgKey, String processId, com.processpuzzle.workflow.model.ProcessInstanceStatus status,
            String entityId, String where, String order, Integer page, Integer size) {
        ProcessInstanceStatus domainStatus = status == null ? null : ProcessInstanceStatus.valueOf(status.getValue());
        var query = new FindAllProcessInstancesUseCase.Query(orgKey, processId, domainStatus, entityId, where, order, page, size);
        var result = findAllProcessInstances.findAll(query);
        return ResponseEntity.ok(mapper.toModel(result));
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
        var workProducts = listWorkProductInstances.findAll(orgKey, instanceId);
        return mapper.toModel(instance, tasks, workProducts);
    }
}
