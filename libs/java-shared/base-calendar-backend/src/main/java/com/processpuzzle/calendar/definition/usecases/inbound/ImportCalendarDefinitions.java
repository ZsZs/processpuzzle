package com.processpuzzle.calendar.definition.usecases.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.domain.CalendarKind;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
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
 * Upserting, all-or-nothing bulk import of a {@code calendar-definitions:} YAML list, for the seed files a host
 * application ships. Existing drafts are replaced rather than rejected, so an edited seed file reaches a
 * stack that has already been seeded. If any entry is invalid nothing is persisted and every problem is
 * reported. A seeded set is a vetted one the running application is meant to have, so with
 * {@code release} the imported definitions also become the released rows — and nothing a designer drafted
 * besides.
 */
@Service
public class ImportCalendarDefinitions {

    private final CalendarDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ImportCalendarDefinitions(CalendarDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, InputStream input, boolean release) throws IOException {
        Map<String, CalendarDefinition> byKey = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Entry entry : parse(input)) {
            CalendarDefinition definition = entry.toDomain(orgKey);
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
        List<String> updatedKeys = new ArrayList<>();
        for (CalendarDefinition definition : byKey.values()) {
            if (write(orgKey, definition, Channel.DRAFT)) {
                updated++;
                updatedKeys.add(definition.getDefinitionKey());
            } else {
                created++;
            }
            if (release) {
                write(orgKey, definition, Channel.RELEASED);
            }
        }
        return new ImportOutcome(created, updated, List.of(), List.copyOf(byKey.keySet()), List.copyOf(updatedKeys));
    }

    /**
     * Deletes every draft of the organization, for a starter that replaces what is there. The released rows go with
     * the next release, which removes the released rows that have no draft.
     *
     * @return the keys removed
     */
    @Transactional
    public List<String> removeAll(String orgKey) {
        List<CalendarDefinition> drafts = repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(orgKey, Channel.DRAFT);
        repository.deleteAll(drafts);
        return drafts.stream().map(CalendarDefinition::getDefinitionKey).toList();
    }

    /** @return whether the row existed and was replaced */
    private boolean write(String orgKey, CalendarDefinition definition, Channel channel) {
        Optional<CalendarDefinition> existing =
                repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definition.getDefinitionKey(), channel);
        if (existing.isPresent()) {
            existing.get().replaceWith(definition);
            repository.save(existing.get());
            return true;
        }
        CalendarDefinition row = CalendarDefinition.releasedCopyOf(definition);
        row.setChannel(channel);
        repository.save(row);
        return false;
    }

    private List<Entry> parse(InputStream input) throws IOException {
        try {
            Document document = yamlMapper.readValue(input, Document.class);
            return document == null || document.calendarDefinitions() == null ? List.of() : document.calendarDefinitions();
        } catch (MismatchedInputException e) {
            // An empty file.
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(@JsonProperty("calendar-definitions") List<Entry> calendarDefinitions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String key, String name, String description, CalendarKind kind, CalendarVisibility visibility,
                 List<String> readRoles, List<String> bookRoles, List<String> manageRoles, PublicDetail publicDetail,
                 List<EntryKind> allowedKinds, Integer capacity, Integer slotMinutes, Integer minDurationMinutes,
                 Integer maxDurationMinutes, Integer horizonDays) {

        CalendarDefinition toDomain(String orgKey) {
            return CalendarDefinition.builder()
                    .orgKey(orgKey)
                    .definitionKey(key)
                    .name(name)
                    .description(description)
                    .kind(kind == null ? CalendarKind.TEAM : kind)
                    .visibility(visibility == null ? CalendarVisibility.AUTHENTICATED : visibility)
                    .readRoles(copy(readRoles))
                    .bookRoles(copy(bookRoles))
                    .manageRoles(copy(manageRoles))
                    .publicDetail(publicDetail == null ? PublicDetail.BUSY_ONLY : publicDetail)
                    .allowedKinds(copy(allowedKinds))
                    .capacity(capacity == null ? 1 : capacity)
                    .slotMinutes(slotMinutes)
                    .minDurationMinutes(minDurationMinutes)
                    .maxDurationMinutes(maxDurationMinutes)
                    .horizonDays(horizonDays)
                    .build();
        }

        private static <T> List<T> copy(List<T> values) {
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        }
    }
}
