package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the module's published {@link EntityObjectAccess} straight over {@link
 * EntityObjectRepository}.
 *
 * <p>Deliberately <em>not</em> routed through {@code UpdateEntityInstanceUseCase}. That use case
 * replaces the whole payload and re-runs payload validation, neither of which is right for a caller
 * that owns exactly one attribute — and it publishes {@code EntityObjectUpdatedEvent}, which the
 * single-attribute write must not, or base-state writing a state would wake its own listeners in a
 * loop. Single-attribute writes are a privileged internal channel, not an ordinary update.
 */
@Component
@RequiredArgsConstructor
public class EntityObjectAccessAdapter implements EntityObjectAccess {

    private final EntityObjectRepository repository;

    @Override
    @Transactional(readOnly = true)
    public EntityObjectView find(String entityDefinitionCode, UUID objectId) {
        EntityObject entityObject = load(entityDefinitionCode, objectId);
        return new EntityObjectView(entityObject.getId(), version(entityObject), entityObject.getPayload());
    }

    @Override
    @Transactional
    public long updateAttribute(String entityDefinitionCode, UUID objectId, String attributeCode,
                                String value, long expectedVersion) {
        EntityObject entityObject = load(entityDefinitionCode, objectId);
        if (version(entityObject) != expectedVersion) {
            throw new EntityObjectAccessException.VersionConflict(
                objectId, expectedVersion, version(entityObject));
        }

        // A copy, not the managed map: mutating the payload in place leaves the field the same
        // instance Hibernate loaded, and a JSON-converted attribute is only flushed when the
        // reference changes.
        Map<String, Object> payload = new LinkedHashMap<>(entityObject.getPayload());
        payload.put(attributeCode, value);
        entityObject.setPayload(payload);

        return version(repository.saveAndFlush(entityObject));
    }

    private EntityObject load(String entityDefinitionCode, UUID objectId) {
        EntityObject entityObject = repository.findById(objectId)
            .orElseThrow(() -> new EntityObjectAccessException.NotFound(entityDefinitionCode, objectId));

        if (!entityObject.getEntityDefinitionCode().equals(entityDefinitionCode)) {
            throw new EntityObjectAccessException.NotFound(entityDefinitionCode, objectId);
        }
        return entityObject;
    }

    /** A never-persisted object has a null version; nothing loaded here can, but the type allows it. */
    private long version(EntityObject entityObject) {
        return entityObject.getVersion() == null ? 0L : entityObject.getVersion();
    }
}
