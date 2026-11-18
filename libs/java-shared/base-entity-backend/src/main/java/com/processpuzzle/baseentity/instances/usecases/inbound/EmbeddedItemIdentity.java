package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The identity every item of a multi-valued embedded collection carries: an {@code id}, assigned once, and an
 * {@code itemVersion}, which counts the changes made to that item through the item endpoints.
 *
 * <p>Items written before this existed, and items a form saves without either member, get both here, on every save of
 * the owning object — so the first write of an object gives its collections the identity the item endpoints address
 * them by. Nothing is backfilled in bulk: an object that is never saved keeps its items as they are, and the item
 * endpoints assign identity on the first change they make.
 *
 * <p>The walk follows the definitions all the way down: a collection nested in an item (the shelves of a zone) gets the
 * same identity as a top-level one, the definition of each level being looked up by the component type it links to.
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
     * The payload with every item of its multi-valued embedded collections carrying an id and an item version, at every
     * level the definitions reach. The input is not changed; a payload with nothing to add is returned as it is.
     *
     * @param definitions the definition of a component type, by its code; a level it cannot find is left as it is
     */
    public static Map<String, Object> withIdentities(EntityDefinitionView definition, Map<String, Object> payload,
                                                     Function<String, Optional<EntityDefinitionView>> definitions) {
        if (payload == null || definition == null) {
            return payload;
        }
        Map<String, Object> result = null;
        for (EntityAttributeView attribute : definition.attributes()) {
            if (!attribute.embeddedComponent() || !attribute.multiValued() || !(payload.get(attribute.code()) instanceof List<?> items)) {
                continue;
            }
            List<Object> identified = identifyAll(items, componentOf(attribute, definitions), definitions);
            if (identified != null) {
                if (result == null) {
                    result = new LinkedHashMap<>(payload);
                }
                result.put(attribute.code(), identified);
            }
        }
        return result == null ? payload : result;
    }

    /** The top level only: the collections nested in an item are left as they are. */
    public static Map<String, Object> withIdentities(EntityDefinitionView definition, Map<String, Object> payload) {
        return withIdentities(definition, payload, code -> Optional.empty());
    }

    private static EntityDefinitionView componentOf(EntityAttributeView attribute, Function<String, Optional<EntityDefinitionView>> definitions) {
        return attribute.linkedEntityType() == null ? null : definitions.apply(attribute.linkedEntityType()).orElse(null);
    }

    /** The items with identity added, at every level below them, or {@code null} when nothing had to change. */
    @SuppressWarnings("unchecked")
    private static List<Object> identifyAll(List<?> items, EntityDefinitionView component, Function<String, Optional<EntityDefinitionView>> definitions) {
        List<Object> identified = new ArrayList<>(items.size());
        boolean changed = false;
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> itemMap = (Map<String, Object>) map;
                Map<String, Object> withIdentity = identify(itemMap);
                Map<String, Object> base = withIdentity == null ? itemMap : withIdentity;
                Map<String, Object> deeper = withIdentities(component, base, definitions);
                changed |= deeper != itemMap;
                identified.add(deeper);
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
