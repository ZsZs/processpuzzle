package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import cz.jirutka.rsql.parser.RSQLParserException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
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
    private final EntityObjectScope scope;
    private final RsqlToInstanceSpecificationPort rsqlSpecificationPort;
    private final EntityDefinitionLookupPort definitionLookupPort;

    @Override
    @Transactional(readOnly = true)
    public EntityObjectView find(String orgKey, String entityDefinitionCode, UUID objectId) {
        EntityObject entityObject = load(orgKey, entityDefinitionCode, objectId);
        return new EntityObjectView(entityObject.getId(), version(entityObject), entityObject.getPayload());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntityObjectView> findAll(String orgKey, String entityDefinitionCode) {
        return scope.findAll(orgKey, entityDefinitionCode).stream()
            .map(entityObject -> new EntityObjectView(
                entityObject.getId(), version(entityObject), entityObject.getPayload()))
            .toList();
    }

    @Override
    @Transactional
    public long updateAttribute(String orgKey, String entityDefinitionCode, UUID objectId, String attributeCode,
                                String value, long expectedVersion) {
        EntityObject entityObject = load(orgKey, entityDefinitionCode, objectId);
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

    @Override
    @Transactional(readOnly = true)
    public List<EntityObjectView> findWhere(String orgKey, String entityDefinitionCode, String rsql) {
        if (rsql == null || rsql.isBlank()) {
            return findAll(orgKey, entityDefinitionCode);
        }
        String storageOrgKey = scope.storageOrgKey(orgKey, entityDefinitionCode);
        Specification<EntityObject> specification;
        try {
            specification = rsqlSpecificationPort.toSpecification(orgKey, rsql, entityDefinitionCode);
        } catch (RSQLParserException e) {
            throw new IllegalArgumentException("Not valid RSQL: " + rsql, e);
        }
        Specification<EntityObject> scoped = specification
            .and((root, query, cb) -> cb.equal(root.get("orgKey"), storageOrgKey))
            .and((root, query, cb) -> cb.equal(root.get("entityDefinitionCode"), entityDefinitionCode));
        return repository.findAll(scoped).stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public void updateAttributes(String orgKey, String entityDefinitionCode, UUID objectId, Map<String, Object> values,
                                 long expectedVersion) {
        EntityObject entityObject = load(orgKey, entityDefinitionCode, objectId);
        if (version(entityObject) != expectedVersion) {
            throw new EntityObjectAccessException.VersionConflict(objectId, expectedVersion, version(entityObject));
        }
        writeKeepingVersion(entityObject, values);
    }

    @Override
    @Transactional
    public boolean upsertByKey(String orgKey, String entityDefinitionCode, Map<String, Object> key, Map<String, Object> values) {
        if (definitionLookupPort.findByCode(orgKey, entityDefinitionCode).isEmpty()) {
            throw new EntityObjectAccessException.UnknownType(entityDefinitionCode);
        }
        for (EntityObject entityObject : scope.findAll(orgKey, entityDefinitionCode)) {
            if (key.entrySet().stream().allMatch(entry -> sameValue(entityObject.getPayload().get(entry.getKey()), entry.getValue()))) {
                writeKeepingVersion(entityObject, values);
                return false;
            }
        }
        repository.save(EntityObject.builder()
            .orgKey(scope.storageOrgKey(orgKey, entityDefinitionCode))
            .entityDefinitionCode(entityDefinitionCode)
            .payload(new LinkedHashMap<>(values))
            .build());
        return true;
    }

    private void writeKeepingVersion(EntityObject entityObject, Map<String, Object> values) {
        Map<String, Object> payload = new LinkedHashMap<>(entityObject.getPayload());
        payload.putAll(values);
        int written = repository.replacePayloadKeepingVersion(entityObject.getId(), entityObject.getOrgKey(),
            entityObject.getVersion(), payload);
        if (written == 0) {
            // Moved on between the read above and this write.
            throw new EntityObjectAccessException.VersionConflict(entityObject.getId(), version(entityObject), version(entityObject) + 1);
        }
    }

    /** Numbers by value — a stored {@code 1} and a written {@code 1.0} are one key — anything else by equality. */
    private static boolean sameValue(Object stored, Object wanted) {
        if (stored instanceof Number left && wanted instanceof Number right) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        return Objects.equals(stored, wanted);
    }

    private EntityObjectView view(EntityObject entityObject) {
        return new EntityObjectView(entityObject.getId(), version(entityObject), entityObject.getPayload());
    }

    /** Another organization's object, or one of another type, is not found — see {@link EntityObjectScope#find}. */
    private EntityObject load(String orgKey, String entityDefinitionCode, UUID objectId) {
        return scope.find(orgKey, entityDefinitionCode, objectId)
            .orElseThrow(() -> new EntityObjectAccessException.NotFound(entityDefinitionCode, objectId));
    }

    /** A never-persisted object has a null version; nothing loaded here can, but the type allows it. */
    private long version(EntityObject entityObject) {
        return entityObject.getVersion() == null ? 0L : entityObject.getVersion();
    }
}
