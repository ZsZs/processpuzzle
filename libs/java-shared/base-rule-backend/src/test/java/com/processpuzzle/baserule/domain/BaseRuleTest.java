package com.processpuzzle.baserule.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseRuleTest {

    @Test
    void constructor_shouldExposeNameAndPriority() {
        BaseRule rule = new BaseRule("credit-check", 5);

        assertEquals("credit-check", rule.getName());
        assertEquals(5, rule.getPriority());
    }

    @Test
    void constructor_shouldDefaultPriorityToZero() {
        BaseRule rule = new BaseRule("credit-check");

        assertEquals(0, rule.getPriority());
    }

    @Test
    void describe_shouldFormatNameAndPriority() {
        BaseRule rule = new BaseRule("credit-check", 5);

        assertEquals("credit-check (priority 5)", rule.describe());
    }
}
