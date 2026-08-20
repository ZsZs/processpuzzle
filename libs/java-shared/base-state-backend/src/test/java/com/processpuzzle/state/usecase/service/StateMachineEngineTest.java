package com.processpuzzle.state.usecase.service;

import com.processpuzzle.state.domain.ActionRef;
import com.processpuzzle.state.domain.GuardRef;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.state.usecase.AvailableTransitionProjection;
import com.processpuzzle.state.usecase.TransitionOutcome;
import com.processpuzzle.state.usecase.exception.UnknownTriggerException;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.port.GuardResult;
import com.processpuzzle.state.usecase.port.TransitionAction;
import com.processpuzzle.state.usecase.port.TransitionGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateMachineEngineTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();

    private GuardActionResolver guardActionResolver;
    private StateMachineEngine engine;
    private StateMachineDefinition definition;

    @BeforeEach
    void setUp() {
        guardActionResolver = mock(GuardActionResolver.class);
        engine = new StateMachineEngine(guardActionResolver);

        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, true, false, null),
                new State("rejected", "Rejected", null, true, false, null));
        List<Transition> transitions = List.of(
                new Transition("approveTransition", null, "draft", "approved", "approve",
                        List.of(new GuardRef("approvalGuard", null)), List.of(new ActionRef("notifyAction", null))),
                new Transition("rejectTransition", null, "draft", "rejected", "reject", List.of(), List.of()));
        definition = StateMachineDefinition.builder()
                .orgKey("org-1")
                .entityName("invoice")
                .name("Invoice workflow")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(states)
                .transitions(transitions)
                .build();
    }

    @Test
    void fireThrowsWhenTriggerIsUnknownAnywhereOnTheMachine() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        assertThatThrownBy(() -> engine.fire(definition, OBJECT_ID, "draft", "cancel", snapshot, null))
                .isInstanceOf(UnknownTriggerException.class);
    }

    @Test
    void fireRejectsWhenNoTransitionMatchesFromTheCurrentState() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        // "approve" exists on the machine, but not from "rejected".
        TransitionOutcome outcome = engine.fire(definition, OBJECT_ID, "rejected", "approve", snapshot, null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.transitionKey()).isNull();
        assertThat(outcome.rejectionReason()).contains("approve").contains("rejected");
    }

    @Test
    void fireRejectsWhenAGuardFails() {
        TransitionGuard guard = mock(TransitionGuard.class);
        when(guard.evaluate(any())).thenReturn(GuardResult.rejected("insufficient balance"));
        when(guardActionResolver.resolveGuard("approvalGuard")).thenReturn(guard);

        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        TransitionOutcome outcome = engine.fire(definition, OBJECT_ID, "draft", "approve", snapshot, null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.transitionKey()).isEqualTo("approveTransition");
        assertThat(outcome.rejectionReason()).isEqualTo("insufficient balance");
        assertThat(outcome.executedActions()).isEmpty();
    }

    @Test
    void fireRunsActionsOnlyAfterEveryGuardPasses() {
        TransitionGuard guard = mock(TransitionGuard.class);
        when(guard.evaluate(any())).thenReturn(GuardResult.allowed());
        when(guardActionResolver.resolveGuard("approvalGuard")).thenReturn(guard);

        TransitionAction action = mock(TransitionAction.class);
        when(guardActionResolver.resolveAction("notifyAction")).thenReturn(action);

        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        TransitionOutcome outcome = engine.fire(definition, OBJECT_ID, "draft", "approve", snapshot, null);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.newStateKey()).isEqualTo("approved");
        assertThat(outcome.executedActions()).containsExactly("notifyAction");
        verify(action).execute(any());
    }

    @Test
    void availableTransitionsIsADryRunThatNeverExecutesActions() {
        TransitionGuard guard = mock(TransitionGuard.class);
        when(guard.evaluate(any())).thenReturn(GuardResult.allowed());
        when(guardActionResolver.resolveGuard("approvalGuard")).thenReturn(guard);

        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        List<AvailableTransitionProjection> available =
                engine.availableTransitions(definition, OBJECT_ID, "draft", snapshot);

        assertThat(available).hasSize(2);
        assertThat(available.get(0).transitionKey()).isEqualTo("approveTransition");
        assertThat(available.get(0).guardsSatisfied()).isTrue();
        assertThat(available.get(1).transitionKey()).isEqualTo("rejectTransition");
        assertThat(available.get(1).guardsSatisfied()).isTrue();
        verify(guardActionResolver, never()).resolveAction(anyString());
    }
}
