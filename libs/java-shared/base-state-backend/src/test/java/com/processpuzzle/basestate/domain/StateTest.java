package com.processpuzzle.basestate.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateTest {

    @Test
    void rejectsBlankKey() {
        assertThatThrownBy(() -> new State("", "Draft", null, false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new State("draft", " ", null, false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAMinimalValidState() {
        State state = new State("draft", "Draft", null, false, false, null);
        assertThat(state.key()).isEqualTo("draft");
        assertThat(state.isFinal()).isFalse();
        assertThat(state.isLocked()).isFalse();
    }

    @Test
    void transitionDefaultsNullGuardsAndActionsToEmptyLists() {
        Transition transition = new Transition("submit", null, "draft", "pendingApproval", "submit", null, null);
        assertThat(transition.guards()).isEmpty();
        assertThat(transition.actions()).isEmpty();
    }

    @Test
    void transitionRejectsBlankTriggerKey() {
        assertThatThrownBy(() -> new Transition("submit", null, "draft", "pendingApproval", " ", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardRefRejectsBlankBeanName() {
        assertThatThrownBy(() -> new GuardRef(" ", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actionRefRejectsBlankBeanName() {
        assertThatThrownBy(() -> new ActionRef("", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
