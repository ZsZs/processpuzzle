package com.processpuzzle.chat.definition.usecases.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.Multiplicity;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.core.definition.Channel;
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
 * Upserting, all-or-nothing bulk import of a {@code chat-definitions:} YAML list, for the seed files a host
 * application ships. Existing drafts are replaced rather than rejected, so an edited seed file reaches a
 * stack that has already been seeded. If any entry is invalid nothing is persisted and every problem is
 * reported. A seeded set is a vetted one the running application is meant to have, so with
 * {@code release} the imported definitions also become the released rows — and nothing a designer drafted
 * besides.
 */
@Service
public class ImportChatDefinitions {

    private final ChatDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ImportChatDefinitions(ChatDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, InputStream input, boolean release) throws IOException {
        Map<String, ChatDefinition> byKey = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Entry entry : parse(input)) {
            ChatDefinition definition = entry.toDomain(orgKey);
            List<String> violations = definition.validate();
            if (!violations.isEmpty()) {
                errors.add("'%s': %s".formatted(definition.getDefinitionKey(), String.join("; ", violations)));
            } else if (byKey.put(definition.getDefinitionKey(), definition) != null) {
                errors.add("Duplicate key within the import file: '%s'".formatted(definition.getDefinitionKey()));
            }
        }
        if (!errors.isEmpty()) {
            return ImportOutcome.rejected(errors);
        }

        int created = 0;
        int updated = 0;
        for (ChatDefinition definition : byKey.values()) {
            if (write(orgKey, definition, Channel.DRAFT)) {
                updated++;
            } else {
                created++;
            }
            if (release) {
                write(orgKey, definition, Channel.RELEASED);
            }
        }
        return new ImportOutcome(created, updated, List.of(), List.copyOf(byKey.keySet()));
    }

    /** @return whether the row existed and was replaced */
    private boolean write(String orgKey, ChatDefinition definition, Channel channel) {
        Optional<ChatDefinition> existing =
                repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definition.getDefinitionKey(), channel);
        if (existing.isPresent()) {
            existing.get().replaceWith(definition);
            repository.save(existing.get());
            return true;
        }
        ChatDefinition row = ChatDefinition.releasedCopyOf(definition);
        row.setChannel(channel);
        repository.save(row);
        return false;
    }

    private List<Entry> parse(InputStream input) throws IOException {
        try {
            Document document = yamlMapper.readValue(input, Document.class);
            return document == null || document.chatDefinitions() == null ? List.of() : document.chatDefinitions();
        } catch (MismatchedInputException e) {
            // An empty file.
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(@JsonProperty("chat-definitions") List<Entry> chatDefinitions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String key, String name, String description, SubjectType subjectType, String subjectDefinition,
                 Multiplicity multiplicity, ChatLifecycle lifecycle, List<String> writableStates,
                 List<String> readRoles, List<String> writeRoles, List<String> createRoles,
                 Boolean includeSubjectOwner, Integer retentionDays) {

        ChatDefinition toDomain(String orgKey) {
            return ChatDefinition.builder()
                    .orgKey(orgKey)
                    .definitionKey(key)
                    .name(name)
                    .description(description)
                    .subjectType(subjectType)
                    .subjectDefinition(subjectDefinition)
                    .multiplicity(multiplicity == null ? Multiplicity.SINGLE : multiplicity)
                    .lifecycle(lifecycle == null ? ChatLifecycle.ON_DEMAND : lifecycle)
                    .writableStates(copy(writableStates))
                    .readRoles(copy(readRoles))
                    .writeRoles(copy(writeRoles))
                    .createRoles(copy(createRoles))
                    .includeSubjectOwner(Boolean.TRUE.equals(includeSubjectOwner))
                    .retentionDays(retentionDays)
                    .build();
        }

        private static List<String> copy(List<String> values) {
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        }
    }
}
