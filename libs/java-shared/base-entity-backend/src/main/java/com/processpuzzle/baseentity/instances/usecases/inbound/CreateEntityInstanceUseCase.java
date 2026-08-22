package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
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
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final PayloadValidatorPort payloadValidatorPort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param orgKey the organization the request was addressed to. Not persisted — {@code
     *               EntityObject} has no organization column — but carried into {@link
     *               EntityObjectCreatedEvent}, because an observer resolving its own metadata for
     *               this object (base-state resolving a state machine) needs the tenant and the
     *               request path is the only place it is known.
     */
    public EntityObject create(String orgKey, String entityDefinitionCode, Map<String, Object> payload) {
        EntityDefinitionView definition = definitionLookupPort.findByCode(entityDefinitionCode)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(entityDefinitionCode)));

        if (definition.embedded()) {
            throw new ConflictException(
                "'%s' is an embedded-component definition and has no instances of its own — it travels inside its parent's payload"
                    .formatted(entityDefinitionCode));
        }

        payloadValidatorPort.validate(definition, payload);

        EntityObject created = repository.saveAndFlush(EntityObject.builder()
            .entityDefinitionCode(entityDefinitionCode)
            .payload(payload)
            .build());

        // saveAndFlush above, so the id and version the event carries are the persisted ones rather
        // than nulls a listener would have to read back.
        eventPublisher.publishEvent(new EntityObjectCreatedEvent(
            orgKey, entityDefinitionCode, created.getId(), created.getPayload(),
            created.getVersion() == null ? 0L : created.getVersion(), Instant.now()));
        return created;
    }
}
