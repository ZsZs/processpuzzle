package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.List;

/**
 * @param titleAttribute code of the attribute that names an object of this type — the definition's
 *                       {@code isLinkToDetails} attribute, e.g. {@code orderNumber}; null when none is marked
 */
public record EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes, String titleAttribute) {

    public EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes) {
        this(code, embedded, attributes, null);
    }

    public EntityAttributeView attribute(String code) {
        return attributes.stream()
            .filter(a -> a.code().equals(code))
            .findFirst()
            .orElse(null);
    }
}
