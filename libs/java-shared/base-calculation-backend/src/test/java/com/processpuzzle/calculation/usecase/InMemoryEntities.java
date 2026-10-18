package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.port.AttributeType;
import com.processpuzzle.calculation.port.EntityAttributeTypePort;
import com.processpuzzle.calculation.port.EntityQueryPort;
import com.processpuzzle.calculation.port.EntityRecord;
import com.processpuzzle.calculation.port.EntityWriteConflict;
import com.processpuzzle.calculation.port.EntityWritePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The entity ports over maps, for tests. RSQL is understood as far as {@code attribute==value}. Writes keep the
 * version, as base-entity's adapter does for derived values.
 */
public class InMemoryEntities implements EntityQueryPort, EntityWritePort, EntityAttributeTypePort {

    private final Map<String, Map<String, EntityRecord>> objects = new LinkedHashMap<>();
    private final Map<String, AttributeType> types = new LinkedHashMap<>();
    public final List<String> queries = new ArrayList<>();
    /** Moves an object on between a run's read and its write, as a concurrent edit would. */
    public boolean moveBeforeWrite;

    public String add(String entity, Map<String, Object> payload) {
        String id = UUID.randomUUID().toString();
        objects.computeIfAbsent(entity, key -> new LinkedHashMap<>()).put(id, new EntityRecord(id, 0, payload));
        return id;
    }

    public void type(String entity, String attribute, AttributeType type) {
        types.put(entity + "." + attribute, type);
    }

    public void bumpVersion(String entity, String id) {
        EntityRecord stored = objects.get(entity).get(id);
        objects.get(entity).put(id, new EntityRecord(id, stored.version() + 1, stored.payload()));
    }

    public Map<String, Object> payload(String entity, String id) {
        return objects.get(entity).get(id).payload();
    }

    public List<EntityRecord> all(String entity) {
        return List.copyOf(objects.getOrDefault(entity, Map.of()).values());
    }

    public void clear() {
        objects.clear();
        queries.clear();
        moveBeforeWrite = false;
    }

    @Override
    public Optional<EntityRecord> find(String orgKey, String entity, String id) {
        return Optional.ofNullable(objects.getOrDefault(entity, Map.of()).get(id));
    }

    @Override
    public List<EntityRecord> findWhere(String orgKey, String entity, String rsql) {
        queries.add(entity + "?" + rsql);
        return all(entity).stream().filter(stored -> {
            if (rsql == null) {
                return true;
            }
            String[] comparison = rsql.split("==", 2);
            return Objects.equals(String.valueOf(stored.payload().get(comparison[0])), comparison[1].replace("\"", ""));
        }).toList();
    }

    @Override
    public void updateAttributes(String orgKey, String entity, String id, Map<String, Object> values, long expectedVersion) {
        if (moveBeforeWrite) {
            bumpVersion(entity, id);
        }
        EntityRecord stored = find(orgKey, entity, id).orElseThrow(() -> new EntityWriteConflict("gone"));
        if (stored.version() != expectedVersion) {
            throw new EntityWriteConflict("'%s' is at version %d, not %d".formatted(id, stored.version(), expectedVersion));
        }
        Map<String, Object> payload = new LinkedHashMap<>(stored.payload());
        payload.putAll(values);
        objects.get(entity).put(id, new EntityRecord(id, stored.version(), payload));
    }

    @Override
    public boolean upsertByKey(String orgKey, String entity, Map<String, Object> key, Map<String, Object> values) {
        for (EntityRecord stored : all(entity)) {
            if (key.entrySet().stream().allMatch(entry -> Objects.equals(stored.payload().get(entry.getKey()), entry.getValue()))) {
                Map<String, Object> payload = new LinkedHashMap<>(stored.payload());
                payload.putAll(values);
                objects.get(entity).put(stored.id(), new EntityRecord(stored.id(), stored.version(), payload));
                return false;
            }
        }
        add(entity, values);
        return true;
    }

    @Override
    public Optional<AttributeType> typeOf(String orgKey, String entity, String attribute) {
        return Optional.ofNullable(types.get(entity + "." + attribute));
    }
}
