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
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityObjectScope scope;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditorAware<String> auditor;

    /**
     * Deletes the object — or, for a shared type, retires it: nothing is hard deleted in a common, the object keeps
     * resolving by id wherever it is referenced, and only lists leave it out. Retiring needs no reference check
     * for that reason, and retiring a retired object is a no-op that reports nothing.
     *
     * @param orgKey see {@code UpdateEntityInstanceUseCase.update} for what is not found.
     */
    public void delete(String orgKey, String entityDefinitionCode, UUID id, boolean cascade) {
        scope.requireWritable(orgKey, entityDefinitionCode);
        EntityObject entityObject = scope.find(orgKey, entityDefinitionCode, id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));
        scope.requireOwnedBy(orgKey, entityDefinitionCode, entityObject);

        Instant now = Instant.now();
        if (scope.isShared(orgKey, entityDefinitionCode)) {
            if (entityObject.isRetired()) {
                return;
            }
            entityObject.setRetiredAt(now);
            entityObject.setRetiredBy(auditor.getCurrentAuditor().orElse(null));
            repository.saveAndFlush(entityObject);
        } else {
            if (!cascade && repository.existsAnyReferenceTo(id.toString(), scope.referringOrgKeys(orgKey, entityDefinitionCode))) {
                throw new ConflictException(
                    "'%s' is still referenced by other entities — pass cascade=true to delete anyway".formatted(id));
            }
            repository.delete(entityObject);
        }

        eventPublisher.publishEvent(new EntityObjectDeletedEvent(orgKey, entityDefinitionCode, id, now));
        eventPublisher.publishEvent(PlatformEvent.of(
            orgKey, entityDefinitionCode, id.toString(), PlatformEventAction.DELETED, null, now));
    }
}
