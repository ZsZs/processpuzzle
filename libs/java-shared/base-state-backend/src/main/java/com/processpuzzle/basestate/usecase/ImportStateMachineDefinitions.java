package com.processpuzzle.basestate.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.basestate.adapter.inbound.dto.StateMachineYamlDocument;
import com.processpuzzle.basestate.adapter.inbound.dto.StateMachineYamlEntry;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
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

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        StateMachineYamlDocument document;
        try {
            document = yamlMapper.readValue(input, StateMachineYamlDocument.class);
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            return new ImportOutcome(0, 0, List.of());
        }
        List<StateMachineYamlEntry> entries = (document == null || document.stateMachines() == null)
                ? List.of()
                : document.stateMachines();

        List<String> errors = new ArrayList<>();
        Map<String, StateMachineYamlEntry> byEntityName = new LinkedHashMap<>();
        for (StateMachineYamlEntry entry : entries) {
            if (entry.entityName() == null || entry.entityName().isBlank()) {
                errors.add("A state machine entry is missing 'entityName' and was skipped.");
                continue;
            }
            if (byEntityName.put(entry.entityName(), entry) != null) {
                errors.add("Duplicate entityName within the import file: '" + entry.entityName() + "'.");
            }
        }

        for (StateMachineYamlEntry entry : byEntityName.values()) {
            try {
                validator.validate(entry.initialStateKey(), entry.states(), entry.transitions());
            } catch (IllegalArgumentException e) {
                errors.add("'" + entry.entityName() + "': " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        int created = 0;
        int updated = 0;
        for (StateMachineYamlEntry entry : byEntityName.values()) {
            Optional<StateMachineDefinition> existing = repository.findByOrgKeyAndEntityName(orgKey, entry.entityName());
            StateMachineDefinition definition;
            if (existing.isPresent()) {
                definition = existing.get();
                definition.replaceTopology(entry.name(), entry.description(), entry.stateAttributeKey(),
                        entry.initialStateKey(), entry.states(), entry.transitions());
                updated++;
            } else {
                definition = new StateMachineDefinition(orgKey, entry.entityName(), entry.name(), entry.description(),
                        entry.stateAttributeKey(), entry.initialStateKey(), entry.states(), entry.transitions());
                created++;
            }
            repository.save(definition);
        }

        return new ImportOutcome(created, updated, errors);
    }
}
