package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.List;

public record EntityDefinitionView(String code, boolean embedded, List<EntityAttributeView> attributes) {

    public EntityAttributeView attribute(String code) {
        return attributes.stream()
            .filter(a -> a.code().equals(code))
            .findFirst()
            .orElse(null);
    }
}
