package com.processpuzzle.state.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.state.adapter.inbound.dto.StateMachineYamlDocument;
import com.processpuzzle.state.adapter.inbound.dto.StateMachineYamlEntry;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All-or-nothing bulk import, the same contract as {@code ImportRules}: if any entry fails
 * validation, nothing in the file is persisted and the full list of problems is returned.
 */
@Service
public class ImportStateMachineDefinitions {

    private final StateMachineDefinitionRepository repository;
    private final StateMachineTopologyValidator validator;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportStateMachineDefinitions(StateMachineDefinitionRepository repository,
                                         StateMachineTopologyValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        List<StateMachineYamlEntry> entries = parseEntries(input);
        if (entries.isEmpty()) {
            return new ImportOutcome(0, 0, List.of());
        }

        List<String> errors = new ArrayList<>();
        Map<String, StateMachineYamlEntry> byEntityName = collectEntriesByEntity(entries, errors);
        validateTopologies(byEntityName, errors);

        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        return persistEntries(orgKey, byEntityName.values());
    }

    private List<StateMachineYamlEntry> parseEntries(InputStream input) throws IOException {
        try {
            StateMachineYamlDocument document = yamlMapper.readValue(input, StateMachineYamlDocument.class);
            return (document == null || document.stateMachines() == null) ? List.of() : document.stateMachines();
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            return List.of();
        }
    }

    private Map<String, StateMachineYamlEntry> collectEntriesByEntity(List<StateMachineYamlEntry> entries, List<String> errors) {
        Map<String, StateMachineYamlEntry> byEntityName = new LinkedHashMap<>();
        for (StateMachineYamlEntry entry : entries) {
            if (entry.entityName() == null || entry.entityName().isBlank()) {
                errors.add("A state machine entry is missing 'entityName' and was skipped.");
            } else if (byEntityName.put(entry.entityName(), entry) != null) {
                errors.add("Duplicate entityName within the import file: '" + entry.entityName() + "'.");
            }
        }
        return byEntityName;
    }

    private void validateTopologies(Map<String, StateMachineYamlEntry> byEntityName, List<String> errors) {
        for (StateMachineYamlEntry entry : byEntityName.values()) {
            try {
                validator.validate(entry.entityName(), entry.stateAttributeKey(),
                        entry.initialStateKey(), entry.states(), entry.transitions());
            } catch (IllegalArgumentException e) {
                errors.add("'" + entry.entityName() + "': " + e.getMessage());
            }
        }
    }

    private ImportOutcome persistEntries(String orgKey, Iterable<StateMachineYamlEntry> entries) {
        int created = 0;
        int updated = 0;
        for (StateMachineYamlEntry entry : entries) {
            Optional<StateMachineDefinition> existing = repository.findByOrgKeyAndEntityName(orgKey, entry.entityName());
            if (existing.isPresent()) {
                StateMachineDefinition definition = existing.get();
                definition.replaceTopology(entry.name(), entry.description(), entry.stateAttributeKey(),
                        entry.initialStateKey(), entry.states(), entry.transitions());
                repository.save(definition);
                updated++;
            } else {
                StateMachineDefinition definition = StateMachineDefinition.builder()
                        .orgKey(orgKey)
                        .entityName(entry.entityName())
                        .name(entry.name())
                        .description(entry.description())
                        .stateAttributeKey(entry.stateAttributeKey())
                        .initialStateKey(entry.initialStateKey())
                        .states(entry.states())
                        .transitions(entry.transitions())
                        .build();
                repository.save(definition);
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of());
    }
}
