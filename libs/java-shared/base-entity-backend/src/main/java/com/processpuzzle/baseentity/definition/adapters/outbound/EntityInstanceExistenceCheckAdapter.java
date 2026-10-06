package com.processpuzzle.baseentity.definition.adapters.outbound;

import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the definition module's outbound port by reaching into the instances module.
 */
@Component
@RequiredArgsConstructor
public class EntityInstanceExistenceCheckAdapter implements EntityInstanceExistenceCheckPort {

    private final EntityObjectScope entityObjectScope;

    @Override
    public boolean existsAnyInstanceOf(String orgKey, String entityDefinitionCode) {
        return entityObjectScope.existsAny(orgKey, entityDefinitionCode);
    }
}
