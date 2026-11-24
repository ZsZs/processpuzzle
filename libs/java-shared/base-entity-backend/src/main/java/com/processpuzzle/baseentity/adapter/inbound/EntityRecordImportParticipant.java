package com.processpuzzle.baseentity.adapter.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.baseentity.adapter.inbound.dto.DefaultEntitiesDocument;
import com.processpuzzle.baseentity.instances.domain.RecordPlanner.RecordInput;
import com.processpuzzle.baseentity.instances.usecases.inbound.LoadRecordsUseCase;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.core.definition.RecordImportParticipant;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Loads a bundle's seed records, written in the {@code default-entities/} YAML format (an {@code entities} list of
 * {@code entityDefinitionCode} and {@code payload}), through {@link LoadRecordsUseCase}. Runs inside the importer's
 * transaction: a refused record refuses the file, and the importer rolls the bundle back.
 */
@Component
public class EntityRecordImportParticipant implements RecordImportParticipant {

    private final LoadRecordsUseCase loader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public EntityRecordImportParticipant(LoadRecordsUseCase loader) {
        this.loader = loader;
    }

    @Override
    public ParticipantResult load(String orgKey, String fileName, byte[] yaml) {
        List<RecordInput> records;
        try {
            DefaultEntitiesDocument document = yamlMapper.readValue(yaml, DefaultEntitiesDocument.class);
            records = document.entities().stream()
                    .map(entity -> new RecordInput(entity.getEntityDefinitionCode(), entity.getPayload()))
                    .toList();
        } catch (IOException e) {
            return ParticipantResult.rejected(List.of("The file is not a list of entity records: " + e.getMessage()));
        }

        LoadRecordsUseCase.Report report = loader.load(orgKey, records, false);
        if (report.hasRejected()) {
            return ParticipantResult.rejected(report.items().stream()
                    .filter(item -> item.action() == LoadRecordsUseCase.Action.REJECTED)
                    .map(item -> "Record " + (item.index() + 1) + " ('" + item.code() + "') is refused: " + item.detail() + ".")
                    .toList());
        }
        return new ParticipantResult(report.items().stream()
                .filter(item -> item.action() == LoadRecordsUseCase.Action.CREATED)
                .map(item -> new ImportedItem(item.code() + " " + item.id(), ImportedItem.Action.CREATE))
                .toList(), List.of());
    }
}
