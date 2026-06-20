package com.processpuzzle.basestate.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseStateTest {

    @Test
    void constructor_shouldDefaultIsFinalToFalse() {
        BaseState state = new BaseState("approved");

        assertEquals("approved", state.getName());
        assertFalse(state.isFinal());
    }

    @Test
    void describe_shouldMarkFinalStates() {
        BaseState state = new BaseState("closed", true);

        assertTrue(state.isFinal());
        assertEquals("closed (final)", state.describe());
    }

    @Test
    void describe_shouldReturnOnlyNameForNonFinalStates() {
        BaseState state = new BaseState("open");

        assertEquals("open", state.describe());
    }
}
