package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteAttributeUseCase {

    private final EntityDefinitionRepository repository;

    public void deleteAttribute(String definitionCode, String attributeCode) {
        BaseEntityDefinition definition = repository.findByCode(definitionCode)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(definitionCode)));

        BaseEntityAttribute attribute = definition.getAttributes().stream()
            .filter(a -> a.getCode().equals(attributeCode))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                "'%s' has no attribute '%s'".formatted(definitionCode, attributeCode)));

        // NOTE: the "still populated on existing instances" 409 guard from the original OpenAPI
        // spec needs an outbound port into the instances module (does any payload still set this
        // attribute code?), same shape as EntityInstanceExistenceCheckPort. Deferred here pending
        // the same RSQL/query-capability decision that's blocking the instances RSQL adapter.
        definition.getAttributes().remove(attribute);
        repository.save(definition);
    }
}
