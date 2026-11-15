package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The identity every item of a multi-valued embedded collection carries: an {@code id}, assigned once, and an
 * {@code itemVersion}, which counts the changes made to that item through the item endpoints.
 *
 * <p>Items written before this existed, and items a form saves without either member, get both here, on every save of
 * the owning object — so the first write of an object gives its collections the identity the item endpoints address
 * them by. Nothing is backfilled in bulk: an object that is never saved keeps its items as they are, and the item
 * endpoints assign identity on the first change they make.
 *
 * <p>Only the top level of an object's payload is walked. The collections of an embedded row are not reached here;
 * the item endpoints address the top-level collections of the entity they are called on.
 */
public final class EmbeddedItemIdentity {

    public static final String ITEM_ID = "id";
    public static final String ITEM_VERSION = "itemVersion";
    /**
     * The version a new item starts at. An {@code int}, deliberately: the payload is JSON, and a stored whole number reads
     * back as an Integer. A Long here would compare unequal to its own stored copy, so every flush of the object would
     * rewrite it and bump its version.
     */
    public static final int FIRST_VERSION = 1;

    private EmbeddedItemIdentity() {
    }

    /**
     * The payload with every item of its multi-valued embedded collections carrying an id and an item version. The input
     * is not changed; a payload with nothing to add is returned as it is.
     */
    public static Map<String, Object> withIdentities(EntityDefinitionView definition, Map<String, Object> payload) {
        if (payload == null || definition == null) {
            return payload;
        }
        Map<String, Object> result = null;
        for (EntityAttributeView attribute : definition.attributes()) {
            if (!attribute.embeddedComponent() || !attribute.multiValued() || !(payload.get(attribute.code()) instanceof List<?> items)) {
                continue;
            }
            List<Object> identified = identifyAll(items);
            if (identified != null) {
                if (result == null) {
                    result = new LinkedHashMap<>(payload);
                }
                result.put(attribute.code(), identified);
            }
        }
        return result == null ? payload : result;
    }

    /** The items with identity added, or {@code null} when every item already had it. */
    private static List<Object> identifyAll(List<?> items) {
        List<Object> identified = new ArrayList<>(items.size());
        boolean changed = false;
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> withIdentity = identify(map);
                changed |= withIdentity != null;
                identified.add(withIdentity == null ? map : withIdentity);
            } else {
                identified.add(item);
            }
        }
        return changed ? identified : null;
    }

    /** The item with the identity it lacks added, or {@code null} when it has all of it. */
    private static Map<String, Object> identify(Map<?, ?> item) {
        boolean missingId = item.get(ITEM_ID) == null || item.get(ITEM_ID).toString().isBlank();
        boolean missingVersion = !(item.get(ITEM_VERSION) instanceof Number);
        if (!missingId && !missingVersion) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        item.forEach((key, value) -> copy.put(String.valueOf(key), value));
        if (missingId) {
            copy.put(ITEM_ID, UUID.randomUUID().toString());
        }
        if (missingVersion) {
            copy.put(ITEM_VERSION, FIRST_VERSION);
        }
        return copy;
    }
}
