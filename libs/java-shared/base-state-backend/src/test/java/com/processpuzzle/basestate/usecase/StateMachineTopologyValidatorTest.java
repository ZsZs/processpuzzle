package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.ActionRef;
import com.processpuzzle.basestate.domain.GuardRef;
import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.usecase.service.GuardActionResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateMachineTopologyValidatorTest {

    private GuardActionResolver guardActionResolver;
    private StateMachineTopologyValidator validator;

    @BeforeEach
    void setUp() {
        guardActionResolver = mock(GuardActionResolver.class);
        when(guardActionResolver.isKnownGuard("knownGuard")).thenReturn(true);
        when(guardActionResolver.isKnownAction("knownAction")).thenReturn(true);
        validator = new StateMachineTopologyValidator(guardActionResolver);
    }

    @Test
    void acceptsAValidTopology() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, true, false, null));
        List<Transition> transitions = List.of(new Transition(
                "approve", null, "draft", "approved", "approve",
                List.of(new GuardRef("knownGuard", null)),
                List.of(new ActionRef("knownAction", null))));

        assertThatCode(() -> validator.validate("draft", states, transitions)).doesNotThrowAnyException();
    }

    @Test
    void acceptsNullTransitions() {
        List<State> states = List.of(new State("draft", "Draft", null, false, false, null));
        assertThatCode(() -> validator.validate("draft", states, null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyOrNullStates() {
        assertThatThrownBy(() -> validator.validate("draft", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one state");

        assertThatThrownBy(() -> validator.validate("draft", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one state");
    }

    @Test
    void rejectsUnknownInitialStateKey() {
        List<State> states = List.of(new State("draft", "Draft", null, false, false, null));
        assertThatThrownBy(() -> validator.validate("nonexistent", states, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialStateKey");
    }

    @Test
    void rejectsTransitionSourcedFromAFinalState() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("closed", "Closed", null, true, false, null));
        List<Transition> transitions = List.of(
                new Transition("reopen", null, "closed", "draft", "reopen", List.of(), List.of()));

        assertThatThrownBy(() -> validator.validate("draft", states, transitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("final state");
    }

    @Test
    void rejectsAmbiguousTrigger() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, false, false, null),
                new State("rejected", "Rejected", null, false, false, null));
        List<Transition> transitions = List.of(
                new Transition("t1", null, "draft", "approved", "decide", List.of(), List.of()),
                new Transition("t2", null, "draft", "rejected", "decide", List.of(), List.of()));

        assertThatThrownBy(() -> validator.validate("draft", states, transitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ambiguous trigger");
    }

    @Test
    void rejectsDuplicateTransitionKey() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, false, false, null));
        List<Transition> transitions = List.of(
                new Transition("t1", null, "draft", "approved", "trig1", List.of(), List.of()),
                new Transition("t1", null, "draft", "approved", "trig2", List.of(), List.of()));

        assertThatThrownBy(() -> validator.validate("draft", states, transitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate transition key");
    }

    @Test
    void rejectsUnknownTransitionSourceOrTargetState() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, false, false, null));

        List<Transition> unknownSource = List.of(
                new Transition("t1", null, "unknown", "approved", "trig1", List.of(), List.of()));
        assertThatThrownBy(() -> validator.validate("draft", states, unknownSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceStateKey");

        List<Transition> unknownTarget = List.of(
                new Transition("t2", null, "draft", "unknown", "trig1", List.of(), List.of()));
        assertThatThrownBy(() -> validator.validate("draft", states, unknownTarget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetStateKey");
    }

    @Test
    void rejectsUnknownGuardBean() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, false, false, null));
        List<Transition> transitions = List.of(new Transition(
                "approve", null, "draft", "approved", "approve",
                List.of(new GuardRef("missingGuard", null)), List.of()));

        assertThatThrownBy(() -> validator.validate("draft", states, transitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missingGuard");
    }

    @Test
    void rejectsUnknownActionBean() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, false, false, null));
        List<Transition> transitions = List.of(new Transition(
                "approve", null, "draft", "approved", "approve",
                List.of(), List.of(new ActionRef("missingAction", null))));

        assertThatThrownBy(() -> validator.validate("draft", states, transitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missingAction");
    }

    @Test
    void rejectsDuplicateStateKeys() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("draft", "Draft Again", null, false, false, null));

        assertThatThrownBy(() -> validator.validate("draft", states, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate state key");
    }
}
