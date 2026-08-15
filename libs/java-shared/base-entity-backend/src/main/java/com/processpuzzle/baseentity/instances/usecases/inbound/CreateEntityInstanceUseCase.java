package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final PayloadValidatorPort payloadValidatorPort;

    public EntityObject create(String entityDefinitionCode, Map<String, Object> payload) {
        EntityDefinitionView definition = definitionLookupPort.findByCode(entityDefinitionCode)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(entityDefinitionCode)));

        if (definition.embedded()) {
            throw new ConflictException(
                "'%s' is an embedded-component definition and has no instances of its own — it travels inside its parent's payload"
                    .formatted(entityDefinitionCode));
        }

        payloadValidatorPort.validate(definition, payload);

        return repository.save(EntityObject.builder()
            .entityDefinitionCode(entityDefinitionCode)
            .payload(payload)
            .build());
    }
}
