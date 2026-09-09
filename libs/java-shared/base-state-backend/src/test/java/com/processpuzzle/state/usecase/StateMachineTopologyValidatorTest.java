package com.processpuzzle.state.usecase;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.state.domain.ActionRef;
import com.processpuzzle.state.domain.GuardRef;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.state.usecase.service.GuardActionResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StateMachineTopologyValidatorTest {

    private static final String ENTITY = "order";
    private static final String ATTR = "status";
    private static final List<State> ONE_STATE = List.of(new State("draft", "Draft", null, false, false, null));

    private GuardActionResolver guardActionResolver;
    private EntityAttributeQuery entityAttributeQuery;
    private StateMachineTopologyValidator validator;

    @BeforeEach
    void setUp() {
        guardActionResolver = mock(GuardActionResolver.class);
        when(guardActionResolver.isKnownGuard("knownGuard")).thenReturn(true);
        when(guardActionResolver.isKnownAction("knownAction")).thenReturn(true);

        entityAttributeQuery = mock(EntityAttributeQuery.class);
        when(entityAttributeQuery.entityTypeExists(ENTITY)).thenReturn(true);
        when(entityAttributeQuery.attributeKind(ENTITY, ATTR))
                .thenReturn(Optional.of(EntityAttributeKind.ENUM));

        validator = new StateMachineTopologyValidator(guardActionResolver, entityAttributeQuery);
    }

    private void validate(String initialStateKey, List<State> states, List<Transition> transitions) {
        validator.validate(ENTITY, ATTR, initialStateKey, states, transitions);
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

        assertThatCode(() -> validate("draft", states, transitions)).doesNotThrowAnyException();
    }

    @Test
    void acceptsNullTransitions() {
        List<State> states = List.of(new State("draft", "Draft", null, false, false, null));
        assertThatCode(() -> validate("draft", states, null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyOrNullStates() {
        assertThatThrownBy(() -> validate("draft", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one state");

        assertThatThrownBy(() -> validate("draft", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one state");
    }

    @Test
    void rejectsUnknownInitialStateKey() {
        List<State> states = List.of(new State("draft", "Draft", null, false, false, null));
        assertThatThrownBy(() -> validate("nonexistent", states, List.of()))
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

        assertThatThrownBy(() -> validate("draft", states, transitions))
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

        assertThatThrownBy(() -> validate("draft", states, transitions))
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

        assertThatThrownBy(() -> validate("draft", states, transitions))
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
        assertThatThrownBy(() -> validate("draft", states, unknownSource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceStateKey");

        List<Transition> unknownTarget = List.of(
                new Transition("t2", null, "draft", "unknown", "trig1", List.of(), List.of()));
        assertThatThrownBy(() -> validate("draft", states, unknownTarget))
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

        assertThatThrownBy(() -> validate("draft", states, transitions))
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

        assertThatThrownBy(() -> validate("draft", states, transitions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missingAction");
    }

    @Test
    void rejectsDuplicateStateKeys() {
        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("draft", "Draft Again", null, false, false, null));

        assertThatThrownBy(() -> validate("draft", states, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate state key");
    }

    // --- the state attribute: the "only base-entity-managed entities" restriction ---

    @Test
    void rejectsBlankEntityName() {
        assertThatThrownBy(() -> validator.validate("  ", ATTR, "draft", ONE_STATE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityName is required");
    }

    @Test
    void rejectsBlankStateAttributeKey() {
        assertThatThrownBy(() -> validator.validate(ENTITY, null, "draft", ONE_STATE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stateAttributeKey is required");
    }

    @Test
    void rejectsAnEntityTypeBaseEntityDoesNotManage() {
        assertThatThrownBy(() -> validator.validate("unmanaged", ATTR, "draft", ONE_STATE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an entity type base-entity manages");
    }

    @Test
    void rejectsAnAttributeTheEntityDoesNotDeclare() {
        when(entityAttributeQuery.attributeKind(ENTITY, "noSuchAttr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(ENTITY, "noSuchAttr", "draft", ONE_STATE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an attribute of 'order'");
    }

    /**
     * ENUM is the normal choice — both seeded machines use one — and TEXT the deliberate second
     * allowance, so an enum-only check would reject definitions we ship ourselves.
     */
    @ParameterizedTest
    @EnumSource(value = EntityAttributeKind.class, names = {"TEXT", "ENUM"})
    void acceptsTextAndEnumStateAttributes(EntityAttributeKind kind) {
        when(entityAttributeQuery.attributeKind(ENTITY, ATTR)).thenReturn(Optional.of(kind));

        assertThatCode(() -> validate("draft", ONE_STATE, List.of())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = EntityAttributeKind.class, names = {"TEXT", "ENUM"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryOtherStateAttributeKind(EntityAttributeKind kind) {
        when(entityAttributeQuery.attributeKind(ENTITY, ATTR)).thenReturn(Optional.of(kind));

        assertThatThrownBy(() -> validate("draft", ONE_STATE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a state attribute must be TEXT or ENUM");
    }
}
