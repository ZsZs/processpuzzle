package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.List;

/**
 * @param titleAttribute code of the attribute that names an object of this type — the definition's
 *                       {@code isLinkToDetails} attribute, e.g. {@code orderNumber}; null when none is marked
 * @param name           the definition's display name, e.g. {@code Order} — the context its rules are written for
 * @param identifiers    the natural keys: attribute codes whose values together are unique among the non-retired objects
 */
public record EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes, String titleAttribute, String name,
                                   List<List<String>> identifiers) {

    public EntityDefinitionView {
        identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
    }

    public EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes, String titleAttribute, String name) {
        this(code, embedded, attributes, titleAttribute, name, List.of());
    }

    public EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes) {
        this(code, embedded, attributes, null, null);
    }

    public EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes, String titleAttribute) {
        this(code, embedded, attributes, titleAttribute, null);
    }

    /** The context the definition's rules are written for: its name, or its code when it has none. */
    public String ruleContext() {
        return name == null || name.isBlank() ? code : name;
    }

    public EntityAttributeView attribute(String code) {
        return attributes.stream()
            .filter(a -> a.code().equals(code))
            .findFirst()
            .orElse(null);
    }
}
