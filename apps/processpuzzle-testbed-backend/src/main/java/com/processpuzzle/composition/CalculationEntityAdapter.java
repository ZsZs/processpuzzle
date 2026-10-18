package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.calculation.port.EntityQueryPort;
import com.processpuzzle.calculation.port.EntityRecord;
import com.processpuzzle.calculation.port.EntityWritePort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reads base-calculation's inputs from base-entity and writes its outputs back, through base-entity's
 * {@code EntityObjectAccess}. base-entity's failures are translated into the port's: an object that is not there
 * is not found, one that moved on or a type that does not exist is a {@link EntityWritePort.Conflict}.
 */
@Component
public class CalculationEntityAdapter implements EntityQueryPort, EntityWritePort {

    private final EntityObjectAccess entities;

    public CalculationEntityAdapter(EntityObjectAccess entities) {
        this.entities = entities;
    }

    @Override
    public Optional<EntityRecord> find(String orgKey, String entity, String id) {
        Optional<UUID> objectId = uuidOf(id);
        if (objectId.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(recordOf(entities.find(orgKey, entity, objectId.get())));
        } catch (EntityObjectAccessException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public List<EntityRecord> findWhere(String orgKey, String entity, String rsql) {
        return entities.findWhere(orgKey, entity, rsql).stream().map(CalculationEntityAdapter::recordOf).toList();
    }

    @Override
    public void updateAttributes(String orgKey, String entity, String id, Map<String, Object> values, long expectedVersion) {
        try {
            entities.updateAttributes(orgKey, entity, uuidOf(id).orElseThrow(() -> new Conflict("'%s' is no object id".formatted(id))),
                    values, expectedVersion);
        } catch (EntityObjectAccessException e) {
            throw new Conflict(e.getMessage());
        }
    }

    @Override
    public boolean upsertByKey(String orgKey, String entity, Map<String, Object> key, Map<String, Object> values) {
        try {
            return entities.upsertByKey(orgKey, entity, key, values);
        } catch (EntityObjectAccessException e) {
            throw new Conflict(e.getMessage());
        }
    }

    private static EntityRecord recordOf(EntityObjectView view) {
        return new EntityRecord(view.id().toString(), view.version(), view.payload());
    }

    private static Optional<UUID> uuidOf(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
