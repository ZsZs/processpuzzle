package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.api.ToolDefinitionsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllToolDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceToolDefinitionUseCase;
import com.processpuzzle.workflow.model.ToolDefinition;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Implements the generated {@code ToolDefinitionsApi} (from the "Tool Definitions" tag). */
@RestController
public class ToolDefinitionsEndpoint implements ToolDefinitionsApi {

    private final CreateToolDefinitionUseCase createToolDefinition;
    private final ReplaceToolDefinitionUseCase replaceToolDefinition;
    private final DeleteToolDefinitionUseCase deleteToolDefinition;
    private final FindToolDefinitionUseCase findToolDefinition;
    private final FindAllToolDefinitionsUseCase findAllToolDefinitions;
    private final WorkflowDefinitionMapper mapper;

    public ToolDefinitionsEndpoint(CreateToolDefinitionUseCase createToolDefinition,
                                    ReplaceToolDefinitionUseCase replaceToolDefinition,
                                    DeleteToolDefinitionUseCase deleteToolDefinition,
                                    FindToolDefinitionUseCase findToolDefinition,
                                    FindAllToolDefinitionsUseCase findAllToolDefinitions,
                                    WorkflowDefinitionMapper mapper) {
        this.createToolDefinition = createToolDefinition;
        this.replaceToolDefinition = replaceToolDefinition;
        this.deleteToolDefinition = deleteToolDefinition;
        this.findToolDefinition = findToolDefinition;
        this.findAllToolDefinitions = findAllToolDefinitions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ToolDefinition>> listToolDefinitions(String orgKey, String where, String order) {
        var tools = findAllToolDefinitions.findAll(orgKey, where, order);
        return ResponseEntity.ok(tools.stream().map(mapper::toToolModel).toList());
    }

    @Override
    public ResponseEntity<ToolDefinition> createToolDefinition(String orgKey, ToolDefinitionInput input) {
        var created = createToolDefinition.create(orgKey, mapper.toToolDomain(input));
        return new ResponseEntity<>(mapper.toToolModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ToolDefinition> getToolDefinition(String orgKey, String toolId) {
        return ResponseEntity.ok(mapper.toToolModel(findToolDefinition.findByOrgKeyAndId(orgKey, toolId)));
    }

    @Override
    public ResponseEntity<ToolDefinition> updateToolDefinition(String orgKey, String toolId, ToolDefinitionInput input) {
        var updated = replaceToolDefinition.replace(orgKey, toolId, mapper.toToolDomain(input));
        return ResponseEntity.ok(mapper.toToolModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteToolDefinition(String orgKey, String toolId) {
        deleteToolDefinition.delete(orgKey, toolId);
        return ResponseEntity.noContent().build();
    }
}
