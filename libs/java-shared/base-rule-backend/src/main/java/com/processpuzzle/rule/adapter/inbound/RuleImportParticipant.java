package com.processpuzzle.rule.adapter.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.rule.adapter.inbound.dto.RuleYamlDocument;
import com.processpuzzle.rule.adapter.inbound.dto.RuleYamlEntry;
import com.processpuzzle.rule.usecase.ExportRules;
import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.ImportRules;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's rule files, in the {@code sample-rules/} YAML format, through
 * {@link ImportRules}. The key is the rule id.
 *
 * <p>{@code ImportRules} reports totals only, so which rules already existed is read beforehand from
 * {@link ExportRules} — the same export that answers {@link #fingerprints}.
 */
@Component
public class RuleImportParticipant implements DefinitionImportParticipant {

    private final ImportRules importRules;
    private final ExportRules exportRules;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public RuleImportParticipant(ImportRules importRules, ExportRules exportRules) {
        this.importRules = importRules;
        this.exportRules = exportRules;
    }

    @Override
    public String kind() {
        return DefinitionKinds.RULE;
    }

    @Override
    public int order() {
        return DefinitionKinds.RULE_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        List<RuleYamlEntry> entries;
        try {
            entries = entries(yaml);
        } catch (IOException e) {
            return ParticipantResult.rejected(List.of(fileName + ": not a readable rule file: " + e.getMessage()));
        }
        Set<String> existing;
        ImportOutcome outcome;
        try {
            existing = currentRules(orgKey).keySet();
            outcome = importRules.execute(orgKey, new ByteArrayInputStream(yaml));
        } catch (IOException | RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (!outcome.errors().isEmpty()) {
            return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
        }

        List<ImportedItem> items = entries.stream()
                .map(RuleYamlEntry::id)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> new ImportedItem(id, existing.contains(id) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE))
                .toList();
        return new ParticipantResult(items, List.of());
    }

    @Override
    public List<String> removeAll(String orgKey) {
        return importRules.removeAll(orgKey);
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        currentRules(orgKey).forEach((id, entry) -> {
            if (keys.contains(id)) {
                fingerprints.put(id, CanonicalJson.of(entry, Set.of()));
            }
        });
        return fingerprints;
    }

    private Map<String, RuleYamlEntry> currentRules(String orgKey) {
        try {
            Map<String, RuleYamlEntry> byId = new LinkedHashMap<>();
            entries(exportRules.execute(orgKey, null)).forEach(entry -> byId.put(entry.id(), entry));
            return byId;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read the rules of '" + orgKey + "'", e);
        }
    }

    private List<RuleYamlEntry> entries(byte[] yaml) throws IOException {
        RuleYamlDocument document = yamlMapper.readValue(yaml, RuleYamlDocument.class);
        return document == null || document.rules() == null ? List.of() : document.rules();
    }
}
