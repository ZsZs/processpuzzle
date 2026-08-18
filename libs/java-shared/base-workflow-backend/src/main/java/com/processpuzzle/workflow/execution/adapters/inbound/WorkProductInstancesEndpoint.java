package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.api.WorkProductInstancesApi;
import com.processpuzzle.workflow.execution.usecases.inbound.FindWorkProductInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListWorkProductInstancesUseCase;
import com.processpuzzle.workflow.model.WorkProductInstance;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code WorkProductInstancesApi} (from the "Work Product Instances" tag). */
@RestController
public class WorkProductInstancesEndpoint implements WorkProductInstancesApi {

    private final ListWorkProductInstancesUseCase listWorkProductInstances;
    private final FindWorkProductInstanceUseCase findWorkProductInstance;
    private final WorkflowExecutionMapper mapper;

    public WorkProductInstancesEndpoint(ListWorkProductInstancesUseCase listWorkProductInstances,
                                         FindWorkProductInstanceUseCase findWorkProductInstance,
                                         WorkflowExecutionMapper mapper) {
        this.listWorkProductInstances = listWorkProductInstances;
        this.findWorkProductInstance = findWorkProductInstance;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<WorkProductInstance>> listWorkProductInstances(String orgKey, String instanceId, String where, String order) {
        var workProducts = listWorkProductInstances.findAll(orgKey, UUID.fromString(instanceId));
        return ResponseEntity.ok(workProducts.stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<WorkProductInstance> getWorkProductInstance(String orgKey, String instanceId, String workProductId) {
        var workProduct = findWorkProductInstance.find(orgKey, UUID.fromString(instanceId), workProductId);
        return ResponseEntity.ok(mapper.toModel(workProduct));
    }
}
