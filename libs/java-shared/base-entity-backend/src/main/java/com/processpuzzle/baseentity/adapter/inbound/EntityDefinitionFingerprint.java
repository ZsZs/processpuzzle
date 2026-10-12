package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.core.definition.CanonicalJson;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * A definition's authored content as canonical JSON: what a starter's provenance hashes and what a release
 * compares a draft with its released row by. One place, so that the two cannot disagree about what changed.
 *
 * <p>Ids, versions and audit fields are left out at every depth — they are assigned, not authored, and a draft and
 * its released copy never share them.
 */
@Component
public class EntityDefinitionFingerprint {

    private static final Set<String> VOLATILE_FIELDS =
        Set.of("id", "version", "createdAt", "createdBy", "updatedAt", "updatedBy", "organizationId");

    private final EntityDefinitionMapper mapper;

    public EntityDefinitionFingerprint(EntityDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    /** @return the canonical JSON, or {@code null} for no definition */
    public String of(BaseEntityDefinition definition) {
        if (definition == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.BaseEntityDefinition model = mapper.toModel(definition);
        // Cleared rather than only stripped: canonical JSON is written without a java.time module.
        model.setCreatedAt(null);
        model.setUpdatedAt(null);
        return CanonicalJson.of(model, VOLATILE_FIELDS);
    }
}
