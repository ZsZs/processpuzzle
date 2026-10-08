package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectDeletedEvent;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityObjectScope scope;
    private final ApplicationEventPublisher eventPublisher;

    /** @param orgKey see {@code UpdateEntityInstanceUseCase.update} for what is not found. */
    public void delete(String orgKey, String entityDefinitionCode, UUID id, boolean cascade) {
        EntityObject entityObject = scope.find(orgKey, entityDefinitionCode, id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));

        if (!cascade && repository.existsAnyReferenceTo(id.toString())) {
            throw new ConflictException(
                "'%s' is still referenced by other entities — pass cascade=true to delete anyway".formatted(id));
        }
        repository.delete(entityObject);

        Instant now = Instant.now();
        eventPublisher.publishEvent(new EntityObjectDeletedEvent(orgKey, entityDefinitionCode, id, now));
        eventPublisher.publishEvent(PlatformEvent.of(
            orgKey, entityDefinitionCode, id.toString(), PlatformEventAction.DELETED, null, now));
    }
}
