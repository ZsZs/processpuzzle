package com.processpuzzle.state.adapter.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.state.usecase.ExportStateMachineDefinitions;
import com.processpuzzle.state.usecase.ImportOutcome;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's state machine files, in the {@code default-state-machines/} YAML
 * format, through {@link ImportStateMachineDefinitions}. The key is the governed entity's name.
 *
 * <p>Runs after the entity participant: every machine's {@code stateAttributeKey} is validated
 * against the entity definition it names, which may have arrived in the same bundle.
 */
@Component
public class StateMachineImportParticipant implements DefinitionImportParticipant {

    private final ImportStateMachineDefinitions importDefinitions;
    private final ExportStateMachineDefinitions exportDefinitions;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public StateMachineImportParticipant(ImportStateMachineDefinitions importDefinitions,
                                         ExportStateMachineDefinitions exportDefinitions) {
        this.importDefinitions = importDefinitions;
        this.exportDefinitions = exportDefinitions;
    }

    @Override
    public String kind() {
        return DefinitionKinds.STATE;
    }

    @Override
    public int order() {
        return DefinitionKinds.STATE_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        Set<String> entityNames;
        try {
            entityNames = entries(yaml).keySet();
        } catch (IOException e) {
            return ParticipantResult.rejected(List.of(fileName + ": not a readable state machine file: " + e.getMessage()));
        }
        Set<String> existing;
        ImportOutcome outcome;
        try {
            existing = currentMachines(orgKey).keySet();
            outcome = importDefinitions.execute(orgKey, new ByteArrayInputStream(yaml));
        } catch (IOException | RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (!outcome.errors().isEmpty()) {
            return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
        }

        List<ImportedItem> items = entityNames.stream()
                .map(name -> new ImportedItem(name, existing.contains(name) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE))
                .toList();
        return new ParticipantResult(items, List.of());
    }

    @Override
    public List<String> removeAll(String orgKey) {
        return importDefinitions.removeAll(orgKey);
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        currentMachines(orgKey).forEach((entityName, entry) -> {
            if (keys.contains(entityName)) {
                fingerprints.put(entityName, CanonicalJson.of(entry, Set.of()));
            }
        });
        return fingerprints;
    }

    private Map<String, JsonNode> currentMachines(String orgKey) {
        try {
            return entries(exportDefinitions.execute(orgKey, null));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read the state machines of '" + orgKey + "'", e);
        }
    }

    /** By entity name, read as a tree: only the key is needed here, the use case does the parsing proper. */
    private Map<String, JsonNode> entries(byte[] yaml) throws IOException {
        Map<String, JsonNode> byEntityName = new LinkedHashMap<>();
        JsonNode root = yamlMapper.readTree(yaml);
        JsonNode machines = root == null ? null : root.get("stateMachines");
        if (machines != null) {
            for (JsonNode machine : machines) {
                JsonNode entityName = machine.get("entityName");
                if (entityName != null && entityName.isTextual()) {
                    byEntityName.putIfAbsent(entityName.asText(), machine);
                }
            }
        }
        return byEntityName;
    }
}
