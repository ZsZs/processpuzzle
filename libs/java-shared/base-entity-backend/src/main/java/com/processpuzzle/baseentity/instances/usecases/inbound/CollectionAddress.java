package com.processpuzzle.baseentity.instances.usecases.inbound;

import java.util.List;

/**
 * Where an embedded collection is, inside an entity instance: the attribute holding it at the top, and — for a collection
 * nested in the items of another — the id of each item it is reached through.
 *
 * <p>{@code of("rounds")} is the top-level collection of zones. {@code of("zones").within(zoneId, "shelves")} is the
 * shelves of one zone. The path is as deep as the containment of the definitions, though the endpoints address two
 * levels: a zone's shelves, not a shelf's own collections.
 *
 * @param attributes the attribute at each level, outermost first; never empty
 * @param ownerIds the id of the item each level but the last is reached through; one shorter than {@code attributes}
 */
public record CollectionAddress(List<String> attributes, List<String> ownerIds) {

    public CollectionAddress {
        attributes = List.copyOf(attributes);
        ownerIds = List.copyOf(ownerIds);
        if (attributes.isEmpty() || ownerIds.size() != attributes.size() - 1) {
            throw new IllegalArgumentException("a collection address names one owner item per level but the last");
        }
    }

    /** The top-level collection held by {@code attribute}. */
    public static CollectionAddress of(String attribute) {
        return new CollectionAddress(List.of(attribute), List.of());
    }

    /** The collection held by {@code childAttribute} inside the item {@code ownerId} of this one. */
    public CollectionAddress within(String ownerId, String childAttribute) {
        List<String> nestedAttributes = new java.util.ArrayList<>(attributes);
        nestedAttributes.add(childAttribute);
        List<String> nestedOwners = new java.util.ArrayList<>(ownerIds);
        nestedOwners.add(ownerId);
        return new CollectionAddress(nestedAttributes, nestedOwners);
    }

    /** The attribute of the collection itself, the innermost. */
    public String attribute() {
        return attributes.get(attributes.size() - 1);
    }

    /** How deep the collection is: 1 for a top-level one. */
    public int depth() {
        return attributes.size();
    }
}
