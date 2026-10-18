package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.core.definition.Channel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserting, all-or-nothing bulk import of a file of calculations, one per YAML document. Each document's own text
 * becomes the stored source, comments included. Existing drafts are replaced rather than rejected, so an edited seed
 * file reaches a stack that has already been seeded. If any document is invalid nothing is persisted and every
 * problem is reported.
 */
@Service
public class ImportCalculationDefinitions {

    private final CalculationDefinitionRepository repository;
    private final CalculationValidator validator;

    public ImportCalculationDefinitions(CalculationDefinitionRepository repository, CalculationValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        return execute(orgKey, new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, String file) {
        List<String> errors = new ArrayList<>();
        Map<String, CalculationValidator.Validation> byId = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        List<String> documents = CalculationParser.documents(file);
        for (int index = 0; index < documents.size(); index++) {
            String source = documents.get(index);
            CalculationValidator.Validation validation = validator.validate(source);
            String id = validation.model() == null || validation.model().id() == null
                    ? "document %d".formatted(index + 1) : validation.model().id();
            if (!validation.isValid()) {
                errors.add("'%s': %s".formatted(id, String.join("; ", validation.errors())));
            } else if (byId.put(id, validation) != null) {
                errors.add("Duplicate calculation within the import file: '%s'".formatted(id));
            } else {
                sources.put(id, source);
            }
        }
        if (!errors.isEmpty()) {
            return ImportOutcome.rejected(errors);
        }

        int created = 0;
        int updated = 0;
        for (Map.Entry<String, CalculationValidator.Validation> entry : byId.entrySet()) {
            String id = entry.getKey();
            Optional<CalculationDefinition> existing = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT);
            CalculationDefinition definition = existing.orElseGet(() ->
                    CalculationDefinition.builder().orgKey(orgKey).id(id).channel(Channel.DRAFT).build());
            definition.applySource(sources.get(id), entry.getValue().model());
            repository.save(definition);
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of(), List.copyOf(byId.keySet()));
    }

    /** Deletes every draft of the organization — a starter's first step; returns the ids deleted. */
    @Transactional
    public List<String> removeAll(String orgKey) {
        List<CalculationDefinition> drafts = repository.findByOrgKeyAndChannelOrderByIdAsc(orgKey, Channel.DRAFT);
        repository.deleteAll(drafts);
        return drafts.stream().map(CalculationDefinition::getId).toList();
    }
}
