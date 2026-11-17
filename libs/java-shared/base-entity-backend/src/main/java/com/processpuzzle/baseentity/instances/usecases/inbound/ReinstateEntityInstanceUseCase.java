package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Takes a retired object of a shared type back: it is listed again. Reinstating an object that is not retired changes nothing. */
@Component
@RequiredArgsConstructor
@Transactional
public class ReinstateEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityObjectScope scope;
    private final ApplicationEventPublisher eventPublisher;

    public EntityObject reinstate(String orgKey, String entityDefinitionCode, UUID id) {
        scope.requireWritable(orgKey, entityDefinitionCode);
        EntityObject entityObject = scope.find(orgKey, entityDefinitionCode, id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));
        if (!entityObject.isRetired()) {
            return entityObject;
        }
        // Its key may have been taken while it was retired.
        scope.requireUniqueKeys(orgKey, entityDefinitionCode, entityObject.getPayload(), entityObject.getId());
        entityObject.setRetiredAt(null);
        entityObject.setRetiredBy(null);
        EntityObject reinstated = repository.saveAndFlush(entityObject);
        eventPublisher.publishEvent(PlatformEvent.of(
            orgKey, entityDefinitionCode, id.toString(), PlatformEventAction.UPDATED, null, Instant.now()));
        return reinstated;
    }
}
