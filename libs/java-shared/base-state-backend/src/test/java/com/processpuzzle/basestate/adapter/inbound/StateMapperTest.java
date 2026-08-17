package com.processpuzzle.basestate.adapter.inbound;

import com.processpuzzle.basestate.domain.ActionRef;
import com.processpuzzle.basestate.domain.GuardRef;
import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.model.AvailableTransition;
import com.processpuzzle.basestate.model.EntityObjectStateView;
import com.processpuzzle.basestate.model.PageOfStateMachineDefinition;
import com.processpuzzle.basestate.model.StateMachineDefinitionInput;
import com.processpuzzle.basestate.model.TransitionResult;
import com.processpuzzle.basestate.usecase.AvailableTransitionProjection;
import com.processpuzzle.basestate.usecase.EntityObjectStateProjection;
import com.processpuzzle.basestate.usecase.FireStateTransition;
import com.processpuzzle.basestate.usecase.ImportOutcome;
import com.processpuzzle.basestate.usecase.TransitionOutcome;
import com.processpuzzle.shared.model.ImportResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class StateMapperTest {

    private StateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new StateMapper();
    }

    @Test
    void toDomain_and_applyToDomain_shouldMapCorrectly() {
        com.processpuzzle.basestate.model.State modelState =
                new com.processpuzzle.basestate.model.State("draft", "Draft")
                        .description("Draft state")
                        .terminal(false)
                        .locked(true)
                        .metadata(Map.of("color", "blue"));

        com.processpuzzle.basestate.model.GuardRef modelGuard =
                new com.processpuzzle.basestate.model.GuardRef("guardBean").params(Map.of("p1", "v1"));
        com.processpuzzle.basestate.model.ActionRef modelAction =
                new com.processpuzzle.basestate.model.ActionRef("actionBean").params(Map.of("a1", "v2"));

        com.processpuzzle.basestate.model.Transition modelTransition =
                new com.processpuzzle.basestate.model.Transition("submit", "draft", "submitted", "SUBMIT")
                        .name("Submit Transition")
                        .guards(List.of(modelGuard))
                        .actions(List.of(modelAction));

        StateMachineDefinitionInput input = new StateMachineDefinitionInput(
                "invoice", "Invoice SM", "status", "draft", List.of(modelState))
                .description("SM Desc")
                .transitions(List.of(modelTransition));

        StateMachineDefinition domain = mapper.toDomain("org-1", input);

        assertThat(domain.getOrgKey()).isEqualTo("org-1");
        assertThat(domain.getEntityName()).isEqualTo("invoice");
        assertThat(domain.getName()).isEqualTo("Invoice SM");
        assertThat(domain.getDescription()).isEqualTo("SM Desc");
        assertThat(domain.getStateAttributeKey()).isEqualTo("status");
        assertThat(domain.getInitialStateKey()).isEqualTo("draft");
        assertThat(domain.getStates()).hasSize(1);
        assertThat(domain.getStates().get(0).key()).isEqualTo("draft");
        assertThat(domain.getStates().get(0).isLocked()).isTrue();
        assertThat(domain.getStates().get(0).isFinal()).isFalse();
        assertThat(domain.getStates().get(0).metadata()).isEqualTo(Map.of("color", "blue"));
        assertThat(domain.getTransitions()).hasSize(1);
        assertThat(domain.getTransitions().get(0).key()).isEqualTo("submit");
        assertThat(domain.getTransitions().get(0).guards().get(0).beanName()).isEqualTo("guardBean");
        assertThat(domain.getTransitions().get(0).actions().get(0).beanName()).isEqualTo("actionBean");

        // applyToDomain
        StateMachineDefinition target = StateMachineDefinition.builder()
                .orgKey("org-1")
                .entityName("invoice")
                .name("Old")
                .stateAttributeKey("old")
                .initialStateKey("old")
                .build();
        mapper.applyToDomain(input, target);
        assertThat(target.getName()).isEqualTo("Invoice SM");
        assertThat(target.getDescription()).isEqualTo("SM Desc");
    }

    @Test
    void toDomain_nullStatesAndTransitions_shouldMapEmptyLists() {
        StateMachineDefinitionInput input = new StateMachineDefinitionInput(
                "invoice", "Invoice SM", "status", "draft", null)
                .transitions(null);

        StateMachineDefinition domain = mapper.toDomain("org-1", input);
        assertThat(domain.getStates()).isEmpty();
        assertThat(domain.getTransitions()).isEmpty();

        com.processpuzzle.basestate.model.Transition transitionWithNulls =
                new com.processpuzzle.basestate.model.Transition("submit", "draft", "submitted", "SUBMIT")
                        .guards(null)
                        .actions(null);
        StateMachineDefinitionInput input2 = new StateMachineDefinitionInput(
                "invoice", "Invoice SM", "status", "draft", null)
                .transitions(List.of(transitionWithNulls));

        StateMachineDefinition domain2 = mapper.toDomain("org-1", input2);
        assertThat(domain2.getTransitions().get(0).guards()).isEmpty();
        assertThat(domain2.getTransitions().get(0).actions()).isEmpty();
    }

    @Test
    void toModel_fromStateMachineDefinition_shouldMapCorrectly() {
        State state = new State("draft", "Draft", "Draft desc", true, false, Map.of("key", "val"));
        Transition transition = new Transition(
                "submit", "Submit Name", "draft", "submitted", "SUBMIT",
                List.of(new GuardRef("guard1", Map.of("k", "v"))),
                List.of(new ActionRef("action1", Map.of("k2", "v2")))
        );

        StateMachineDefinition definition = StateMachineDefinition.builder()
                .orgKey("org-1")
                .entityName("invoice")
                .name("Invoice Machine")
                .description("SM Description")
                .stateAttributeKey("status")
                .initialStateKey("draft")
                .states(List.of(state))
                .transitions(List.of(transition))
                .build();

        com.processpuzzle.basestate.model.StateMachineDefinition model = mapper.toModel(definition);

        assertThat(model.getOrgKey()).isEqualTo("org-1");
        assertThat(model.getEntityName()).isEqualTo("invoice");
        assertThat(model.getName()).isEqualTo("Invoice Machine");
        assertThat(model.getDescription()).isEqualTo("SM Description");
        assertThat(model.getStates()).hasSize(1);
        assertThat(model.getStates().get(0).getKey()).isEqualTo("draft");
        assertThat(model.getStates().get(0).getTerminal()).isTrue();
        assertThat(model.getStates().get(0).getLocked()).isFalse();
        assertThat(model.getTransitions()).hasSize(1);
        assertThat(model.getTransitions().get(0).getKey()).isEqualTo("submit");
        assertThat(model.getTransitions().get(0).getGuards().get(0).getBeanName()).isEqualTo("guard1");
        assertThat(model.getTransitions().get(0).getActions().get(0).getBeanName()).isEqualTo("action1");
    }

    @Test
    void toModel_page_shouldMapCorrectly() {
        StateMachineDefinition definition = StateMachineDefinition.builder()
                .orgKey("org-1")
                .entityName("invoice")
                .name("Invoice Machine")
                .stateAttributeKey("status")
                .initialStateKey("draft")
                .states(List.of(new State("draft", "Draft", null, false, false, null)))
                .transitions(List.of())
                .build();

        PageOfStateMachineDefinition pageModel = mapper.toModel(
                new PageImpl<>(List.of(definition), PageRequest.of(0, 10), 1));

        assertThat(pageModel.getContent()).hasSize(1);
        assertThat(pageModel.getTotalElements()).isEqualTo(1);
        assertThat(pageModel.getTotalPages()).isEqualTo(1);
        assertThat(pageModel.getNumber()).isEqualTo(0);
        assertThat(pageModel.getSize()).isEqualTo(10);
    }

    @Test
    void toModel_importOutcome_shouldMapCorrectly() {
        ImportOutcome outcome = new ImportOutcome(3, 2, List.of("error1"));
        ImportResult result = mapper.toModel(outcome);

        assertThat(result.getCreated()).isEqualTo(3);
        assertThat(result.getUpdated()).isEqualTo(2);
        assertThat(result.getErrors()).containsExactly("error1");
    }

    @Test
    void toModel_entityObjectStateProjection_shouldMapCorrectly() {
        UUID objectId = UUID.randomUUID();
        Instant now = Instant.now();
        AvailableTransitionProjection transitionProj = new AvailableTransitionProjection(
                "submit", "SUBMIT", "submitted", false, "Guard failed");
        EntityObjectStateProjection projection = new EntityObjectStateProjection(
                objectId, "invoice", "draft", false, now, List.of(transitionProj));

        EntityObjectStateView view = mapper.toModel(projection);

        assertThat(view.getObjectId()).isEqualTo(objectId);
        assertThat(view.getEntityName()).isEqualTo("invoice");
        assertThat(view.getCurrentStateKey()).isEqualTo("draft");
        assertThat(view.getTerminal()).isFalse();
        assertThat(view.getEnteredStateAt()).isNotNull();
        assertThat(view.getAvailableTransitions()).hasSize(1);
        AvailableTransition at = view.getAvailableTransitions().get(0);
        assertThat(at.getTransitionKey()).isEqualTo("submit");
        assertThat(at.getTriggerKey()).isEqualTo("SUBMIT");
        assertThat(at.getTargetStateKey()).isEqualTo("submitted");
        assertThat(at.getGuardsSatisfied()).isFalse();
        assertThat(at.getBlockedReason()).isEqualTo("Guard failed");
    }

    @Test
    void toModel_fireStateTransitionResult_shouldMapCorrectly() {
        TransitionOutcome outcome = TransitionOutcome.success("draft", "approved", "t1", List.of("action1"));
        FireStateTransition.Result result = new FireStateTransition.Result(outcome, 5L);

        TransitionResult model = mapper.toModel(result);

        assertThat(model.getSuccess()).isTrue();
        assertThat(model.getPreviousStateKey()).isEqualTo("draft");
        assertThat(model.getNewStateKey()).isEqualTo("approved");
        assertThat(model.getTransitionKey()).isEqualTo("t1");
        assertThat(model.getExecutedActions()).containsExactly("action1");
        assertThat(model.getVersion()).isEqualTo(5L);
        assertThat(model.getRejectionReason()).isNull();

        TransitionOutcome rejectedOutcome = TransitionOutcome.rejected("draft", "submit", "Guard rejected");
        FireStateTransition.Result rejectedResult = new FireStateTransition.Result(rejectedOutcome, 2L);

        TransitionResult rejectedModel = mapper.toModel(rejectedResult);
        assertThat(rejectedModel.getSuccess()).isFalse();
        assertThat(rejectedModel.getPreviousStateKey()).isEqualTo("draft");
        assertThat(rejectedModel.getTransitionKey()).isEqualTo("submit");
        assertThat(rejectedModel.getRejectionReason()).isEqualTo("Guard rejected");
        assertThat(rejectedModel.getVersion()).isEqualTo(2L);
    }
}
