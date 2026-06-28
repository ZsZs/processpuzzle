package com.processpuzzle.rule.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.rule.adapter.inbound.dto.RuleYamlDocument;
import com.processpuzzle.rule.adapter.inbound.dto.RuleYamlEntry;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class ImportRules {

    private final RuleDefinitionRepository repository;
    private final RuleEngineSync ruleEngineSync;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportRules(RuleDefinitionRepository repository, RuleEngineSync ruleEngineSync) {
        this.repository = repository;
        this.ruleEngineSync = ruleEngineSync;
    }

    @Transactional
    public ImportOutcome execute(InputStream input) throws IOException {
        RuleYamlDocument document = yamlMapper.readValue(input, RuleYamlDocument.class);
        List<RuleYamlEntry> entries = document.rules() == null ? List.of() : document.rules();

        List<String> errors = new ArrayList<>();
        Map<String, RuleYamlEntry> byId = new LinkedHashMap<>();
        for (RuleYamlEntry entry : entries) {
            if (entry.id() == null || entry.id().isBlank()) {
                errors.add("A rule entry is missing 'id' and was skipped.");
                continue;
            }
            if (byId.put(entry.id(), entry) != null) {
                errors.add("Duplicate rule id within the import file: '" + entry.id() + "'.");
            }
        }

        Map<String, String> extendsLinks = new HashMap<>();
        for (RuleDefinition existing : repository.findAll()) {
            extendsLinks.put(existing.getId(), existing.getExtendsRuleId());
        }
        for (RuleYamlEntry entry : byId.values()) {
            extendsLinks.put(entry.id(), entry.extendsRuleId());
        }

        for (RuleYamlEntry entry : byId.values()) {
            String parentId = entry.extendsRuleId();
            if (parentId == null) {
                continue;
            }
            if (!extendsLinks.containsKey(parentId)) {
                errors.add("Rule '" + entry.id() + "' extends unknown rule '" + parentId + "'.");
                continue;
            }
            if (createsCycle(entry.id(), extendsLinks)) {
                errors.add("Rule '" + entry.id() + "' is part of an extends cycle.");
            }
        }

        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        int created = 0;
        int updated = 0;
        for (RuleYamlEntry entry : byId.values()) {
            Optional<RuleDefinition> existingOpt = repository.findById(entry.id());
            RuleDefinition rule;
            if (existingOpt.isPresent()) {
                rule = existingOpt.get();
                applyEntry(rule, entry);
                updated++;
            } else {
                rule = new RuleDefinition(
                        entry.id(), entry.name(), entry.description(), entry.context(),
                        entry.expression(), parseSeverity(entry.severity()),
                        entry.message(), entry.translocoId(), entry.extendsRuleId(),
                        Boolean.TRUE.equals(entry.override()),
                        entry.enabled() == null || entry.enabled());
                created++;
            }
            repository.save(rule);
            ruleEngineSync.register(rule);
        }

        return new ImportOutcome(created, updated, errors);
    }

    private void applyEntry(RuleDefinition rule, RuleYamlEntry entry) {
        rule.setName(entry.name());
        rule.setDescription(entry.description());
        rule.setContext(entry.context());
        rule.setExpression(entry.expression());
        rule.setSeverity(parseSeverity(entry.severity()));
        rule.setMessage(entry.message());
        rule.setTranslocoId(entry.translocoId());
        rule.setExtendsRuleId(entry.extendsRuleId());
        rule.setOverride(Boolean.TRUE.equals(entry.override()));
        rule.setEnabled(entry.enabled() == null || entry.enabled());
    }

    private Severity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return Severity.ERROR;
        }
        return Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private boolean createsCycle(String startId, Map<String, String> extendsLinks) {
        Set<String> visited = new HashSet<>();
        String cursor = extendsLinks.get(startId);
        while (cursor != null) {
            if (cursor.equals(startId) || !visited.add(cursor)) {
                return true;
            }
            cursor = extendsLinks.get(cursor);
        }
        return false;
    }
}
