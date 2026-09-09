package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the instances module's EntityDefinitionLookupPort by reaching into the definition
 * module and mapping its JPA entities down to the read-only view records the port contract
 * defines.
 */
@Component
@RequiredArgsConstructor
public class EntityDefinitionLookupAdapter implements EntityDefinitionLookupPort {

    private final EntityDefinitionRepository entityDefinitionRepository;

    @Override
    public Optional<EntityDefinitionView> findByCode(String code) {
        return entityDefinitionRepository.findByCode(code).map(this::toView);
    }

    private EntityDefinitionView toView(BaseEntityDefinition definition) {
        return new EntityDefinitionView(
            definition.getCode(),
            definition.isEmbedded(),
            definition.getAttributes().stream().map(this::toView).toList()
        );
    }

    private EntityAttributeView toView(BaseEntityAttribute attribute) {
        return new EntityAttributeView(
            attribute.getCode(),
            ValueKindView.valueOf(attribute.getValueKind().name()),
            attribute.isMultiValued(),
            attribute.getFormControlType() == FormControlType.EMBEDDED_COMPONENTS,
            attribute.getLinkedEntityType(),
            attribute.isRequired()
        );
    }
}
