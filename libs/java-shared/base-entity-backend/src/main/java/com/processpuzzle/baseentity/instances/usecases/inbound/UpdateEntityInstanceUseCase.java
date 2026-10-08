package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
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
    private final EntityObjectScope scope;
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final PayloadValidatorPort payloadValidatorPort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param orgKey see {@code CreateEntityInstanceUseCase.create}; an object another organization owns, or
     *               one of another type than {@code entityDefinitionCode}, is not found
     */
    public EntityObject update(String orgKey, String entityDefinitionCode, UUID id, Long expectedVersion, Map<String, Object> payload) {
        EntityObject entityObject = scope.find(orgKey, entityDefinitionCode, id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));

        if (!entityObject.getVersion().equals(expectedVersion)) {
            throw new ConflictException("version conflict on entity '%s'".formatted(id));
        }

        EntityDefinitionView definition = definitionLookupPort.findByCode(orgKey, entityObject.getEntityDefinitionCode())
            .orElseThrow(() -> new NotFoundException(
                "No entity definition with code '%s'".formatted(entityObject.getEntityDefinitionCode())));
        payloadValidatorPort.validate(orgKey, definition, payload);

        entityObject.setPayload(payload);
        EntityObject updated = repository.saveAndFlush(entityObject);

        Instant now = Instant.now();
        eventPublisher.publishEvent(new EntityObjectUpdatedEvent(
            orgKey, updated.getEntityDefinitionCode(), updated.getId(), updated.getPayload(),
            updated.getVersion() == null ? 0L : updated.getVersion(), now));
        eventPublisher.publishEvent(PlatformEvent.of(
            orgKey, updated.getEntityDefinitionCode(), updated.getId().toString(), PlatformEventAction.UPDATED,
            updated.getPayload(), now));
        return updated;
    }
}
