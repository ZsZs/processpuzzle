package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.MeasureNormalizer;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings the seeded instances of a default definition up to the measure attributes it has now.
 *
 * <p>{@link DefaultEntityLoader} never updates an instance it has seeded, so an object seeded before one of its attributes became a
 * QUANTITY, MONEY or DIMENSIONS value keeps a bare number there: its derived numbers are missing and a calculation over it reads
 * null. This rewrites each such value into its measure object, see {@link MeasureNormalizer#withMeasureObjects}. An object whose
 * values are already measure objects is not written at all, so the upgrade is a no-op from its second boot on.
 */
@Component
@Transactional
public class SeededInstanceUpgrader {

    private final EntityObjectScope objects;
    private final EntityObjectRepository repository;
    private final EntityDefinitionLookupPort definitions;

    public SeededInstanceUpgrader(EntityObjectScope objects, EntityObjectRepository repository, EntityDefinitionLookupPort definitions) {
        this.objects = objects;
        this.repository = repository;
        this.definitions = definitions;
    }

    /**
     * @param orgKey               the organization the instances were seeded into
     * @param entityDefinitionCode the definition whose instances are upgraded
     * @return how many objects were rewritten
     */
    public int upgrade(String orgKey, String entityDefinitionCode) {
        Optional<EntityDefinitionView> definition = definitions.findByCode(orgKey, entityDefinitionCode);
        if (definition.isEmpty()) {
            return 0;
        }
        int rewritten = 0;
        for (EntityObject object : objects.findAll(orgKey, entityDefinitionCode)) {
            Map<String, Object> payload = object.getPayload();
            Map<String, Object> upgraded = MeasureNormalizer.withMeasureObjects(definition.get(), payload,
                    code -> definitions.findByCode(orgKey, code));
            if (!upgraded.equals(payload)
                    && repository.replacePayloadKeepingVersion(object.getId(), object.getOrgKey(), object.getVersion(), upgraded) == 1) {
                rewritten++;
            }
        }
        return rewritten;
    }
}
