package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityObjectScope scope;
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final PayloadValidatorPort payloadValidatorPort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param orgKey the organization the request was addressed to. Persisted on the object, and
     *               carried into {@link EntityObjectCreatedEvent}, because an observer resolving its
     *               own metadata for this object (base-state resolving a state machine) needs the
     *               tenant.
     */
    public EntityObject create(String orgKey, String entityDefinitionCode, Map<String, Object> payload) {
        EntityDefinitionView definition = definitionLookupPort.findByCode(orgKey, entityDefinitionCode)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(entityDefinitionCode)));

        if (definition.embedded()) {
            throw new ConflictException(
                "'%s' is an embedded-component definition and has no instances of its own — it travels inside its parent's payload"
                    .formatted(entityDefinitionCode));
        }

        payloadValidatorPort.validate(orgKey, definition, payload);

        EntityObject created = repository.saveAndFlush(EntityObject.builder()
            .orgKey(scope.storageOrgKey(orgKey, entityDefinitionCode))
            .entityDefinitionCode(entityDefinitionCode)
            .payload(payload)
            .build());

        // saveAndFlush above, so the id and version the event carries are the persisted ones rather
        // than nulls a listener would have to read back.
        Instant now = Instant.now();
        eventPublisher.publishEvent(new EntityObjectCreatedEvent(
            orgKey, entityDefinitionCode, created.getId(), created.getPayload(),
            created.getVersion() == null ? 0L : created.getVersion(), now));
        // The feature-neutral twin of the event above, for base-event's catalog — see PlatformEvent.
        eventPublisher.publishEvent(PlatformEvent.of(
            orgKey, entityDefinitionCode, created.getId().toString(), PlatformEventAction.CREATED,
            created.getPayload(), now));
        return created;
    }
}
