package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.InstanceDataProbe;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Tells a Business Starter install how many entity objects the organization holds: replacing the
 * entity definitions would orphan every one of them.
 */
@Component
public class EntityObjectDataProbe implements InstanceDataProbe {

    private final EntityObjectRepository repository;

    public EntityObjectDataProbe(EntityObjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public String label() {
        return "entity objects";
    }

    @Override
    public long count(String orgKey) {
        return repository.countByOrgKey(orgKey);
    }

    @Override
    public String definitionKind() {
        return DefinitionKinds.ENTITY;
    }

    @Override
    public long count(String orgKey, Collection<String> definitionKeys) {
        return definitionKeys.isEmpty() ? 0 : repository.countByOrgKeyAndEntityDefinitionCodeIn(orgKey, definitionKeys);
    }
}
