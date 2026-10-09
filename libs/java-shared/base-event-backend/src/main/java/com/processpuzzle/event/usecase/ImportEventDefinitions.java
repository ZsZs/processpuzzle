package com.processpuzzle.event.usecase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.shared.event.PlatformEventAction;
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
 * Upserting, all-or-nothing bulk import of an {@code event-definitions:} YAML list. Existing
 * definitions are replaced rather than rejected — unlike the entity and widget seeders — so an
 * edited seed file reaches a stack that has already been seeded. If any entry is invalid nothing is
 * persisted and every problem is reported.
 */
@Service
public class ImportEventDefinitions {

    private final EventDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ImportEventDefinitions(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        List<Entry> entries = parse(input);
        List<String> errors = new ArrayList<>();
        Map<String, EventDefinition> byId = new LinkedHashMap<>();
        for (Entry entry : entries) {
            EventDefinition definition = entry.toDomain(orgKey);
            List<String> violations = definition.validate();
            if (!violations.isEmpty()) {
                errors.add("'%s': %s".formatted(definition.getId(), String.join("; ", violations)));
            } else if (byId.put(definition.getId(), definition) != null) {
                errors.add("Duplicate id within the import file: '%s'".formatted(definition.getId()));
            }
        }
        if (!errors.isEmpty()) {
            return ImportOutcome.rejected(errors);
        }

        int created = 0;
        int updated = 0;
        for (EventDefinition definition : byId.values()) {
            Optional<EventDefinition> existing = repository.findByOrgKeyAndId(orgKey, definition.getId());
            if (existing.isPresent()) {
                existing.get().replaceWith(definition);
                repository.save(existing.get());
                updated++;
            } else {
                repository.save(definition);
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of());
    }

    private List<Entry> parse(InputStream input) throws IOException {
        try {
            Document document = yamlMapper.readValue(input, Document.class);
            return document == null || document.eventDefinitions() == null ? List.of() : document.eventDefinitions();
        } catch (MismatchedInputException e) {
            // An empty file.
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(@JsonProperty("event-definitions") List<Entry> eventDefinitions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String id, String name, String description, EventKind kind, String subjectType,
                 PlatformEventAction action, String state) {

        EventDefinition toDomain(String orgKey) {
            return EventDefinition.builder()
                    .orgKey(orgKey)
                    .id(id)
                    .name(name)
                    .description(description)
                    .kind(kind)
                    .subjectType(subjectType)
                    .action(action)
                    .state(state)
                    .build();
        }
    }
}
