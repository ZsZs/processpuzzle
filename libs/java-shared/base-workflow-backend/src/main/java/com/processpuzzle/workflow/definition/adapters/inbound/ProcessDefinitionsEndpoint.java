package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.api.ProcessDefinitionsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ExportProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllProcessDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportProcessDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceProcessDefinitionUseCase;
import com.processpuzzle.workflow.model.PageOfProcessDefinitionSummary;
import com.processpuzzle.workflow.model.ProcessDefinition;
import com.processpuzzle.workflow.model.ProcessDefinitionInput;
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
 * Implements the generated {@code ProcessDefinitionsApi} (from the "Process Definitions" tag).
 * See {@code WorkflowDefinitionMapper}'s Javadoc for the one field ({@code extends}) whose
 * generated accessor name couldn't be verified without running the code generator.
 */
@RestController
public class ProcessDefinitionsEndpoint implements ProcessDefinitionsApi {

    private final CreateProcessDefinitionUseCase createProcessDefinition;
    private final ReplaceProcessDefinitionUseCase replaceProcessDefinition;
    private final DeleteProcessDefinitionUseCase deleteProcessDefinition;
    private final FindProcessDefinitionUseCase findProcessDefinition;
    private final FindAllProcessDefinitionsUseCase findAllProcessDefinitions;
    private final ImportProcessDefinitionsUseCase importProcessDefinitions;
    private final ExportProcessDefinitionUseCase exportProcessDefinition;
    private final WorkflowDefinitionMapper mapper;

    public ProcessDefinitionsEndpoint(CreateProcessDefinitionUseCase createProcessDefinition,
                                       ReplaceProcessDefinitionUseCase replaceProcessDefinition,
                                       DeleteProcessDefinitionUseCase deleteProcessDefinition,
                                       FindProcessDefinitionUseCase findProcessDefinition,
                                       FindAllProcessDefinitionsUseCase findAllProcessDefinitions,
                                       ImportProcessDefinitionsUseCase importProcessDefinitions,
                                       ExportProcessDefinitionUseCase exportProcessDefinition,
                                       WorkflowDefinitionMapper mapper) {
        this.createProcessDefinition = createProcessDefinition;
        this.replaceProcessDefinition = replaceProcessDefinition;
        this.deleteProcessDefinition = deleteProcessDefinition;
        this.findProcessDefinition = findProcessDefinition;
        this.findAllProcessDefinitions = findAllProcessDefinitions;
        this.importProcessDefinitions = importProcessDefinitions;
        this.exportProcessDefinition = exportProcessDefinition;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<ProcessDefinition> createProcessDefinition(String orgKey, ProcessDefinitionInput input) {
        var created = createProcessDefinition.create(orgKey, mapper.toDomain(orgKey, input));
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ProcessDefinition> getProcessDefinition(String orgKey, String processId) {
        return ResponseEntity.ok(mapper.toModel(findProcessDefinition.findByOrgKeyAndId(orgKey, processId)));
    }

    @Override
    public ResponseEntity<ProcessDefinition> updateProcessDefinition(String orgKey, String processId, ProcessDefinitionInput input) {
        var updated = replaceProcessDefinition.replace(orgKey, processId, mapper.toDomain(orgKey, input));
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteProcessDefinition(String orgKey, String processId) {
        deleteProcessDefinition.delete(orgKey, processId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PageOfProcessDefinitionSummary> listProcessDefinitions(
            String orgKey, String where, String order, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toModel(findAllProcessDefinitions.findAll(orgKey, where, order, page, size)));
    }

    @Override
    public ResponseEntity<ImportResult> importProcessDefinitions(String orgKey, MultipartFile file) {
        try {
            ImportOutcome outcome = importProcessDefinitions.execute(orgKey, file.getInputStream());
            return ResponseEntity.ok(mapper.toModel(outcome));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportProcessDefinition(String orgKey, String processId) {
        try {
            byte[] yaml = exportProcessDefinition.execute(orgKey, processId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + processId + "-export.yaml\"")
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(new ByteArrayResource(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
