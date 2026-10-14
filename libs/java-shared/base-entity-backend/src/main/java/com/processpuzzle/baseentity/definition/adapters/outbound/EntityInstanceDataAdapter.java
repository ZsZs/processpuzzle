package com.processpuzzle.baseentity.definition.adapters.outbound;

import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceDataPort;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the definition module's outbound port by reaching into the instances module.
 */
@Component
@RequiredArgsConstructor
public class EntityInstanceDataAdapter implements EntityInstanceDataPort {

    private final EntityObjectScope entityObjectScope;

    @Override
    public List<Map<String, Object>> payloadsOf(String orgKey, String entityDefinitionCode) {
        return entityObjectScope.payloads(orgKey, entityDefinitionCode);
    }
}
