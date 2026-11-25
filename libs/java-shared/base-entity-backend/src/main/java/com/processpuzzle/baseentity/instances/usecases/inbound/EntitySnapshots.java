package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SnapshotView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The snapshot a reference carries (docs/race-platform-design.md): when a record is written, the attributes of the record
 * its reference names are copied under {@code <attribute>$snapshot}, from the writer's own view of that record.
 *
 * <p>A snapshot is taken when the reference is set or changed. When the reference still names the record it named
 * before, the stored snapshot stands: a later change to the referenced record does not rewrite the referencing one. The
 * snapshot key a client sends is never trusted: it is always replaced here, or removed.
 */
final class EntitySnapshots {

    /** The key a snapshot is stored under, next to the reference it copies. */
    static final String SUFFIX = "$snapshot";

    private EntitySnapshots() {
    }

    /**
     * @param snapshots     the snapshots of the type, by attribute code
     * @param stored        the stored payload before the write, or null for a create
     * @param referenced    the referenced record's payload, for an id and the writer's view of it
     */
    static Map<String, Object> withSnapshots(Map<String, Object> payload, Map<String, SnapshotView> snapshots,
                                             Map<String, Object> stored,
                                             BiFunction<String, UUID, Optional<Map<String, Object>>> referenced) {
        if (snapshots.isEmpty()) {
            return payload;
        }
        Map<String, Object> result = new LinkedHashMap<>(payload);
        snapshots.forEach((attribute, view) -> {
            String key = attribute + SUFFIX;
            Object value = payload.get(attribute);
            result.remove(key);
            if (value == null) {
                return;
            }
            if (!(value instanceof String id)) {
                throw new ConflictException("'%s' takes one reference, so it carries a snapshot of one record".formatted(attribute));
            }
            if (stored != null && id.equals(stored.get(attribute)) && stored.containsKey(key)) {
                result.put(key, stored.get(key));
                return;
            }
            Map<String, Object> target = referenced.apply(view.linkedEntityType(), parse(attribute, id))
                .orElseThrow(() -> new ConflictException(
                    "'%s' names a '%s' record that is not visible here, so it cannot be snapshotted".formatted(attribute, view.linkedEntityType())));
            Map<String, Object> copy = new LinkedHashMap<>();
            view.fields().forEach(field -> copy.put(field, target.get(field)));
            result.put(key, copy);
        });
        return result;
    }

    private static UUID parse(String attribute, String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ConflictException("'%s' does not name a record".formatted(attribute));
        }
    }
}
