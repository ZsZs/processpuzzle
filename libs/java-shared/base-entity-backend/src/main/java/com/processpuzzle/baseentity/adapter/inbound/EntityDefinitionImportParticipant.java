package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionsByCodesUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's entity files, in the {@code default-entities/} YAML format, through
 * {@link ImportEntityDefinitions}. The key is the definition code. Runs first: every other kind may
 * refer to an entity definition.
 */
@Component
public class EntityDefinitionImportParticipant implements DefinitionImportParticipant {

    private final ImportEntityDefinitions importDefinitions;
    private final FindEntityDefinitionsByCodesUseCase findByCodes;
    private final EntityDefinitionFingerprint fingerprint;

    public EntityDefinitionImportParticipant(ImportEntityDefinitions importDefinitions,
                                             FindEntityDefinitionsByCodesUseCase findByCodes,
                                             EntityDefinitionFingerprint fingerprint) {
        this.importDefinitions = importDefinitions;
        this.findByCodes = findByCodes;
        this.fingerprint = fingerprint;
    }

    @Override
    public String kind() {
        return DefinitionKinds.ENTITY;
    }

    @Override
    public int order() {
        return DefinitionKinds.ENTITY_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        ImportEntityDefinitions.Outcome outcome;
        try {
            outcome = importDefinitions.execute(orgKey, new ByteArrayInputStream(yaml));
        } catch (IOException | RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (!outcome.errors().isEmpty()) {
            return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
        }

        List<ImportedItem> items = new ArrayList<>();
        outcome.created().forEach(code -> items.add(new ImportedItem(code, ImportedItem.Action.CREATE)));
        outcome.updated().forEach(code -> items.add(new ImportedItem(code, ImportedItem.Action.UPDATE)));
        return new ParticipantResult(items, List.of());
    }

    @Override
    public List<String> removeAll(String orgKey) {
        return importDefinitions.removeAll(orgKey);
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        findByCodes.find(orgKey, keys, Channel.DRAFT).forEach(definition ->
            fingerprints.put(definition.getCode(), fingerprint.of(definition)));
        return fingerprints;
    }
}
