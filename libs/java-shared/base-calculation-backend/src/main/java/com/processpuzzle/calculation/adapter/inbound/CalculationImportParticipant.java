package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.calculation.usecase.ImportOutcome;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's calculation files — the seed format, one calculation per YAML document — through
 * {@link ImportCalculationDefinitions}. The key is the calculation id; the fingerprint its source.
 */
@Component
public class CalculationImportParticipant implements DefinitionImportParticipant {

    private final ImportCalculationDefinitions importer;
    private final FindCalculationDefinition find;

    public CalculationImportParticipant(ImportCalculationDefinitions importer, FindCalculationDefinition find) {
        this.importer = importer;
        this.find = find;
    }

    @Override
    public String kind() {
        return DefinitionKinds.CALCULATION;
    }

    @Override
    public int order() {
        return DefinitionKinds.CALCULATION_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        Set<String> existing = drafts(orgKey).keySet();
        ImportOutcome outcome;
        try {
            outcome = importer.execute(orgKey, new String(yaml, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (!outcome.errors().isEmpty()) {
            return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
        }
        return new ParticipantResult(outcome.written().stream()
                .map(id -> new ImportedItem(id, existing.contains(id) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE))
                .toList(), List.of());
    }

    @Override
    public List<String> removeAll(String orgKey) {
        return importer.removeAll(orgKey);
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        drafts(orgKey).forEach((id, definition) -> {
            if (keys.contains(id)) {
                fingerprints.put(id, CalculationReleaseParticipant.fingerprint(definition));
            }
        });
        return fingerprints;
    }

    private Map<String, CalculationDefinition> drafts(String orgKey) {
        return find.findAll(orgKey, Channel.DRAFT).stream()
                .collect(Collectors.toMap(CalculationDefinition::getId, definition -> definition, (a, b) -> a, LinkedHashMap::new));
    }
}
