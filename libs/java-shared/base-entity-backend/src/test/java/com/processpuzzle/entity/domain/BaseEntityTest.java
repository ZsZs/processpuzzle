package com.processpuzzle.entity.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityTest {

    @Test
    void constructor_shouldDefaultToNoAttributes() {
        BaseEntity entity = new BaseEntity("customer");

        assertEquals("customer", entity.getName());
        assertEquals(List.of(), entity.getAttributes());
        assertEquals(0, entity.attributeCount());
    }

    @Test
    void constructor_shouldCopyTheGivenAttributes() {
        BaseEntity entity = new BaseEntity("customer", List.of("name", "email"));

        assertEquals(List.of("name", "email"), entity.getAttributes());
        assertEquals(2, entity.attributeCount());
    }

    @Test
    void hasAttribute_shouldRecognizeDeclaredAttributes() {
        BaseEntity entity = new BaseEntity("customer", List.of("name", "email"));

        assertTrue(entity.hasAttribute("email"));
        assertFalse(entity.hasAttribute("phone"));
    }
}
