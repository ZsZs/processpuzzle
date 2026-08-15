package com.processpuzzle.baseentity.definition.adapters.outbound;

import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the definition module's outbound port by reaching into the instances module.
 * <p>
 * TODO: this currently autowires instances' EntityObjectRepository directly, which works in a
 * single-deployable Spring Modulith monolith but doesn't yet respect any explicit module
 * boundary (@NamedInterface / published API) — I don't know your Modulith conventions for
 * base-rule/base-state well enough to guess the right shape here. If those modules expose
 * outbound calls through an explicit published-interface package rather than direct repository
 * access, this adapter should do the same.
 */
@Component
@RequiredArgsConstructor
public class EntityInstanceExistenceCheckAdapter implements EntityInstanceExistenceCheckPort {

    private final EntityObjectRepository entityObjectRepository;

    @Override
    public boolean existsAnyInstanceOf(String entityDefinitionCode) {
        return entityObjectRepository.existsByEntityDefinitionCode(entityDefinitionCode);
    }
}
