package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.port.ManagedAttributesPort;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import com.processpuzzle.baseentity.instances.usecases.outbound.PayloadValidatorPort;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class UpdateEntityInstanceUseCase {

    private final EntityObjectRepository repository;
    private final EntityObjectScope scope;
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final PayloadValidatorPort payloadValidatorPort;
    private final ApplicationEventPublisher eventPublisher;
    private final ManagedAttributesPort managedAttributesPort;

    @Autowired
    public UpdateEntityInstanceUseCase(EntityObjectRepository repository, EntityObjectScope scope,
                                       EntityDefinitionLookupPort definitionLookupPort, PayloadValidatorPort payloadValidatorPort,
                                       ApplicationEventPublisher eventPublisher, ObjectProvider<ManagedAttributesPort> managedAttributesProvider) {
        this(repository, scope, definitionLookupPort, payloadValidatorPort, eventPublisher,
            managedAttributesProvider.getIfUnique(() -> ManagedAttributesPort.NONE));
    }

    public UpdateEntityInstanceUseCase(EntityObjectRepository repository, EntityObjectScope scope,
                                       EntityDefinitionLookupPort definitionLookupPort, PayloadValidatorPort payloadValidatorPort,
                                       ApplicationEventPublisher eventPublisher, ManagedAttributesPort managedAttributesPort) {
        this.repository = repository;
        this.scope = scope;
        this.definitionLookupPort = definitionLookupPort;
        this.payloadValidatorPort = payloadValidatorPort;
        this.eventPublisher = eventPublisher;
        this.managedAttributesPort = managedAttributesPort;
    }

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
        Map<String, Object> effectivePayload = keepManagedAttributes(orgKey, entityObject, payload);
        payloadValidatorPort.validate(orgKey, definition, effectivePayload);

        entityObject.setPayload(effectivePayload);
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

    /**
     * The submitted payload with every {@link ManagedAttributesPort managed} attribute put back to its stored
     * value — or left out, when nothing was stored. Silently, not as a rejection: the generic form sends the
     * whole object back, managed attributes included, and an unchanged value is the common case.
     */
    private Map<String, Object> keepManagedAttributes(String orgKey, EntityObject stored, Map<String, Object> payload) {
        var managed = managedAttributesPort.managedAttributesOf(orgKey, stored.getEntityDefinitionCode());
        if (managed.isEmpty()) {
            return payload;
        }
        Map<String, Object> storedPayload = stored.getPayload() == null ? Map.of() : stored.getPayload();
        Map<String, Object> effective = new HashMap<>(payload);
        managed.forEach(attribute -> {
            if (storedPayload.containsKey(attribute)) {
                effective.put(attribute, storedPayload.get(attribute));
            } else {
                effective.remove(attribute);
            }
        });
        return effective;
    }
}
