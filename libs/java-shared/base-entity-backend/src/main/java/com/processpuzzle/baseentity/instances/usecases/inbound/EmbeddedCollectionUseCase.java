package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

import static com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedItemIdentity.FIRST_VERSION;
import static com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedItemIdentity.ITEM_ID;
import static com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedItemIdentity.ITEM_VERSION;

/**
 * Reads and changes <em>one item</em> of an embedded collection — the components an entity instance carries in a
 * multi-valued EMBEDDED_COMPONENTS attribute — so that a caller adding a round to a race sends the round, not the race.
 * A collection nested in an item (the results of a round) is addressed the same way, through its {@link CollectionAddress}.
 *
 * <p>An item is identified by its {@code id} member, assigned here on append. The write itself is the
 * {@link UpdateEntityInstanceUseCase update} of the whole payload with the one item changed, so an item change is
 * validated, versioned and announced exactly as a form's save is: one place decides what a valid object is, and the
 * object's {@code version} bumps on every item change.
 *
 * <p><b>Concurrency.</b> Two versions are in play. Each item carries an {@code itemVersion}, which counts the changes made
 * to that item; a caller that names the item version it last saw gets an {@link ItemVersionConflictException} when someone
 * else changed the item since, while the rest of the collection stays open to it. The object's own version is what the
 * update underneath checks: a caller that names none is applying its change to <em>whatever is stored</em>, and is retried
 * when another write wins that race — which is what makes appends conflict-free: two devices adding a round at the same
 * moment both succeed.
 *
 * <p>Deliberately not a {@code jsonb_set}: that would write around validation, the managed-attribute guard and the events
 * the update use case already guarantees.
 */
@Component
public class EmbeddedCollectionUseCase {

    private static final int ATTEMPTS = 4;

    /**
     * An item changed since the version the caller named. Not retried: the same stale version would fail again, and the
     * caller has to read the item before deciding what to do with its change.
     */
    public static final class ItemVersionConflictException extends ConflictException {
        public ItemVersionConflictException(String message) {
            super(message);
        }
    }

    private final EntityObjectScope scope;
    private final EntityDefinitionLookupPort definitionLookupPort;
    private final UpdateEntityInstanceUseCase updateUseCase;

    public EmbeddedCollectionUseCase(EntityObjectScope scope, EntityDefinitionLookupPort definitionLookupPort,
                                     UpdateEntityInstanceUseCase updateUseCase) {
        this.scope = scope;
        this.definitionLookupPort = definitionLookupPort;
        this.updateUseCase = updateUseCase;
    }

    /** What a read answers: the items, and the version of the object they were read from. */
    public record Items(List<Map<String, Object>> items, long version) {}

    /**
     * What a write answers: the item as stored ({@code null} for a removal), the item's version and the object's new
     * version.
     */
    public record ItemResult(Map<String, Object> item, Long itemVersion, long version) {}

    // region top-level collections
    public Items list(String orgKey, String entityDefinitionCode, UUID id, String attributeCode) {
        return list(orgKey, entityDefinitionCode, id, CollectionAddress.of(attributeCode));
    }

    public ItemResult append(String orgKey, String entityDefinitionCode, UUID id, String attributeCode, Map<String, Object> item) {
        return append(orgKey, entityDefinitionCode, id, CollectionAddress.of(attributeCode), item);
    }

    public ItemResult patch(String orgKey, String entityDefinitionCode, UUID id, String attributeCode, String itemId,
                            Long expectedItemVersion, Map<String, Object> patch) {
        return patch(orgKey, entityDefinitionCode, id, CollectionAddress.of(attributeCode), itemId, expectedItemVersion, patch);
    }

    public ItemResult remove(String orgKey, String entityDefinitionCode, UUID id, String attributeCode, String itemId, Long expectedItemVersion) {
        return remove(orgKey, entityDefinitionCode, id, CollectionAddress.of(attributeCode), itemId, expectedItemVersion);
    }
    // endregion

    // region addressed collections
    public Items list(String orgKey, String entityDefinitionCode, UUID id, CollectionAddress address) {
        EntityObject object = load(orgKey, entityDefinitionCode, id);
        requireEmbeddedPath(orgKey, object, address);
        return new Items(itemsAt(object.getPayload(), address), object.getVersion());
    }

    public ItemResult append(String orgKey, String entityDefinitionCode, UUID id, CollectionAddress address, Map<String, Object> item) {
        return change(orgKey, entityDefinitionCode, id, address, items -> {
            Map<String, Object> added = new LinkedHashMap<>(item == null ? Map.of() : item);
            Object given = added.get(ITEM_ID);
            if (given == null || given.toString().isBlank()) {
                added.put(ITEM_ID, UUID.randomUUID().toString());
            } else if (indexOf(items, given.toString()) >= 0) {
                throw new ConflictException("an item with id '%s' already exists in '%s'".formatted(given, address.attribute()));
            }
            added.put(ITEM_VERSION, FIRST_VERSION);
            items.add(added);
            return added;
        });
    }

    /**
     * Merge patch: members present replace the stored ones, a {@code null} member is removed; {@code id} stays, and the
     * item's version counts this change. {@code expectedItemVersion}, when named, must be the item's current version.
     */
    public ItemResult patch(String orgKey, String entityDefinitionCode, UUID id, CollectionAddress address, String itemId,
                            Long expectedItemVersion, Map<String, Object> patch) {
        return change(orgKey, entityDefinitionCode, id, address, items -> {
            int index = requireItem(items, itemId, address.attribute());
            Map<String, Object> current = items.get(index);
            checkItemVersion(current, expectedItemVersion, itemId);
            Map<String, Object> merged = new LinkedHashMap<>(current);
            (patch == null ? Map.<String, Object>of() : patch).forEach((key, value) -> {
                if (ITEM_ID.equals(key)) {
                    if (value != null && !itemId.equals(value.toString())) {
                        throw new IllegalArgumentException("the id of an item cannot be changed");
                    }
                } else if (ITEM_VERSION.equals(key)) {
                    // Counted by the server, never set by a caller.
                    return;
                } else if (value == null) {
                    merged.remove(key);
                } else {
                    merged.put(key, value);
                }
            });
            merged.put(ITEM_VERSION, (int) (versionOf(current) + 1));
            items.set(index, merged);
            return merged;
        });
    }

    public ItemResult remove(String orgKey, String entityDefinitionCode, UUID id, CollectionAddress address, String itemId, Long expectedItemVersion) {
        return change(orgKey, entityDefinitionCode, id, address, items -> {
            int index = requireItem(items, itemId, address.attribute());
            checkItemVersion(items.get(index), expectedItemVersion, itemId);
            items.remove(index);
            return null;
        });
    }
    // endregion

    // region private helper methods
    /**
     * Applies {@code operation} to a copy of the collection and saves. Retried while another write wins the race — every
     * attempt reads the item again, so a retry checks it afresh. An item conflict is never retried.
     */
    private ItemResult change(String orgKey, String entityDefinitionCode, UUID id, CollectionAddress address,
                              Function<List<Map<String, Object>>, Map<String, Object>> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return attemptChange(orgKey, entityDefinitionCode, id, address, operation);
            } catch (ItemVersionConflictException conflict) {
                throw conflict;
            } catch (ConflictException conflict) {
                if (attempt >= ATTEMPTS) {
                    throw conflict;
                }
            }
        }
    }

    private ItemResult attemptChange(String orgKey, String entityDefinitionCode, UUID id, CollectionAddress address,
                                     Function<List<Map<String, Object>>, Map<String, Object>> operation) {
        EntityObject object = load(orgKey, entityDefinitionCode, id);
        requireEmbeddedPath(orgKey, object, address);
        Edit edit = editAt(object.getPayload() == null ? Map.of() : object.getPayload(), address, 0, operation);

        EntityObject updated = updateUseCase.update(orgKey, entityDefinitionCode, id, object.getVersion(), edit.container());
        return new ItemResult(edit.item(), edit.item() == null ? null : versionOf(edit.item()), updated.getVersion());
    }

    /** The container with the collection changed, and the item the operation answered with. */
    private record Edit(Map<String, Object> container, Map<String, Object> item) {}

    /**
     * Copy-on-write along the path: each container on the way down is copied, so the stored payload is never changed in
     * place, and the copies are what the update saves.
     */
    private Edit editAt(Map<String, Object> container, CollectionAddress address, int depth,
                        Function<List<Map<String, Object>>, Map<String, Object>> operation) {
        String attribute = address.attributes().get(depth);
        List<Map<String, Object>> items = itemsOf(container, attribute);
        if (depth == address.attributes().size() - 1) {
            Map<String, Object> item = operation.apply(items);
            return new Edit(withCollection(container, attribute, items), item);
        }
        int index = requireItem(items, address.ownerIds().get(depth), attribute);
        Edit inner = editAt(items.get(index), address, depth + 1, operation);
        items.set(index, inner.container());
        return new Edit(withCollection(container, attribute, items), inner.item());
    }

    private static Map<String, Object> withCollection(Map<String, Object> container, String attribute, List<Map<String, Object>> items) {
        Map<String, Object> copy = new LinkedHashMap<>(container);
        copy.put(attribute, items);
        return copy;
    }

    private static void checkItemVersion(Map<String, Object> item, Long expectedItemVersion, String itemId) {
        if (expectedItemVersion != null && expectedItemVersion != versionOf(item)) {
            throw new ItemVersionConflictException("item '%s' changed since version %d".formatted(itemId, expectedItemVersion));
        }
    }

    /** The item's version; an item stored before versions existed counts as its first. */
    private static long versionOf(Map<String, Object> item) {
        return item.get(ITEM_VERSION) instanceof Number number ? number.longValue() : FIRST_VERSION;
    }

    private EntityObject load(String orgKey, String entityDefinitionCode, UUID id) {
        return scope.find(orgKey, entityDefinitionCode, id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));
    }

    /**
     * Each level must be an embedded collection of the level above it: the first of the object's definition, the next of
     * the component type that attribute links to, and so on down.
     */
    private void requireEmbeddedPath(String orgKey, EntityObject object, CollectionAddress address) {
        EntityDefinitionView definition = definitionLookupPort.findByCode(orgKey, object.getEntityDefinitionCode())
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(object.getEntityDefinitionCode())));
        for (int depth = 0; depth < address.attributes().size(); depth++) {
            String attributeCode = address.attributes().get(depth);
            EntityAttributeView attribute = definition.attribute(attributeCode);
            if (attribute == null || !attribute.embeddedComponent()) {
                throw new NotFoundException("'%s' has no embedded collection '%s'".formatted(definition.code(), attributeCode));
            }
            if (!attribute.multiValued()) {
                throw new IllegalArgumentException("'%s' holds a single embedded object, not a collection".formatted(attributeCode));
            }
            if (depth < address.attributes().size() - 1) {
                definition = definitionLookupPort.findByCode(orgKey, attribute.linkedEntityType())
                    .orElseThrow(() -> new NotFoundException("No embedded definition '%s'".formatted(attribute.linkedEntityType())));
            }
        }
    }

    private List<Map<String, Object>> itemsAt(Map<String, Object> payload, CollectionAddress address) {
        Map<String, Object> container = payload == null ? Map.of() : payload;
        for (int depth = 0; depth < address.attributes().size() - 1; depth++) {
            List<Map<String, Object>> items = itemsOf(container, address.attributes().get(depth));
            container = items.get(requireItem(items, address.ownerIds().get(depth), address.attributes().get(depth)));
        }
        return itemsOf(container, address.attribute());
    }

    /** A deep-enough copy: the list and each item are new, so the stored payload is never changed in place. */
    private static List<Map<String, Object>> itemsOf(Map<String, Object> container, String attribute) {
        Object stored = container.get(attribute);
        List<Map<String, Object>> items = new ArrayList<>();
        if (stored instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    items.add(copy);
                }
            }
        }
        return items;
    }

    private static int requireItem(List<Map<String, Object>> items, String itemId, String attribute) {
        int index = indexOf(items, itemId);
        if (index < 0) {
            throw new NotFoundException("No item '%s' in '%s'".formatted(itemId, attribute));
        }
        return index;
    }

    private static int indexOf(List<Map<String, Object>> items, String itemId) {
        for (int i = 0; i < items.size(); i++) {
            Object id = items.get(i).get(ITEM_ID);
            if (id != null && itemId.equals(id.toString())) {
                return i;
            }
        }
        return -1;
    }
    // endregion
}
