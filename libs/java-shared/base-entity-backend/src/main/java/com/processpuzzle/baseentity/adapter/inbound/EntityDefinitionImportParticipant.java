package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionsByCodesUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.core.definition.CanonicalJson;
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

    /** Assigned by the database or by auditing, so not part of what a starter author wrote. */
    private static final Set<String> VOLATILE_FIELDS =
        Set.of("id", "version", "createdAt", "createdBy", "updatedAt", "updatedBy", "organizationId");

    private final ImportEntityDefinitions importDefinitions;
    private final FindEntityDefinitionsByCodesUseCase findByCodes;
    private final EntityDefinitionMapper mapper;

    public EntityDefinitionImportParticipant(ImportEntityDefinitions importDefinitions,
                                             FindEntityDefinitionsByCodesUseCase findByCodes,
                                             EntityDefinitionMapper mapper) {
        this.importDefinitions = importDefinitions;
        this.findByCodes = findByCodes;
        this.mapper = mapper;
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
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        findByCodes.find(orgKey, keys).forEach(definition ->
            fingerprints.put(definition.getCode(), CanonicalJson.of(withoutTimestamps(mapper.toModel(definition)), VOLATILE_FIELDS)));
        return fingerprints;
    }

    /** Cleared rather than only stripped: canonical JSON is written without a java.time module. */
    private com.processpuzzle.baseentity.model.BaseEntityDefinition withoutTimestamps(
        com.processpuzzle.baseentity.model.BaseEntityDefinition model) {
        model.setCreatedAt(null);
        model.setUpdatedAt(null);
        return model;
    }
}
