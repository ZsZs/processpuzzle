package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class UpdateEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final PayloadValidatorPort payloadValidatorPort;
    private final ApplicationEventPublisher eventPublisher;

    /** @param orgKey see {@code CreateEntityInstanceUseCase.create} — carried into the event only. */
    public EntityObject update(String orgKey, UUID id, Long expectedVersion, Map<String, Object> payload) {
        EntityObject entityObject = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));

        if (!entityObject.getVersion().equals(expectedVersion)) {
            throw new ConflictException("version conflict on entity '%s'".formatted(id));
        }

        EntityDefinitionView definition = definitionLookupPort.findByCode(entityObject.getEntityDefinitionCode())
            .orElseThrow(() -> new NotFoundException(
                "No entity definition with code '%s'".formatted(entityObject.getEntityDefinitionCode())));
        payloadValidatorPort.validate(definition, payload);

        entityObject.setPayload(payload);
        EntityObject updated = repository.saveAndFlush(entityObject);

        eventPublisher.publishEvent(new EntityObjectUpdatedEvent(
            orgKey, updated.getEntityDefinitionCode(), updated.getId(), updated.getPayload(),
            updated.getVersion() == null ? 0L : updated.getVersion(), Instant.now()));
        return updated;
    }
}
