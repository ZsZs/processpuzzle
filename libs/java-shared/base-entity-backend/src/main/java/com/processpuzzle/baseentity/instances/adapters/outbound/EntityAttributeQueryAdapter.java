package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the module's published {@link EntityAttributeQuery} over the existing {@link
 * EntityDefinitionLookupPort}, rather than reaching into the definition module a second time: that
 * port already maps a {@code BaseEntityDefinition} down to a view, and a second path to the same
 * metadata would be a second thing to keep in step.
 */
@Component
@RequiredArgsConstructor
public class EntityAttributeQueryAdapter implements EntityAttributeQuery {

    private final EntityDefinitionLookupPort definitionLookupPort;

    @Override
    public Optional<EntityAttributeKind> attributeKind(String entityDefinitionCode, String attributeCode) {
        return definitionLookupPort.findByCode(entityDefinitionCode)
            .map(definition -> definition.attribute(attributeCode))
            .map(EntityAttributeView::valueKind)
            // valueOf, not a switch: the two enums are kept in step by name, so a kind added to one
            // side only fails here loudly instead of being silently mapped to something plausible.
            .map(kind -> EntityAttributeKind.valueOf(kind.name()));
    }

    @Override
    public boolean entityTypeExists(String entityDefinitionCode) {
        return definitionLookupPort.findByCode(entityDefinitionCode).isPresent();
    }
}
