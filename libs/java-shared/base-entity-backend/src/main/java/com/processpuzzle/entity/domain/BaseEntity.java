package com.processpuzzle.entity.domain;

import java.util.Collections;
import java.util.List;

public class BaseEntity {
    private final String name;
    private final List<String> attributes;

    public BaseEntity(String name) {
        this(name, Collections.emptyList());
    }

    public BaseEntity(String name, List<String> attributes) {
        this.name = name;
        this.attributes = List.copyOf(attributes);
    }

    public String getName() {
        return name;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public int attributeCount() {
        return attributes.size();
    }

    public boolean hasAttribute(String attributeName) {
        return attributes.contains(attributeName);
    }
}
