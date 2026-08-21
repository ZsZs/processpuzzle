package com.processpuzzle.state.domain;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineDefinitionTest {

    @Test
    void testBuilderGettersSettersAndMethods() {
        State draft = new State("draft", "Draft", "Draft state", false, false, null);
        State approved = new State("approved", "Approved", "Approved state", true, false, null);
        Transition t1 = new Transition("approve", "Approve", "draft", "approved", "approve", List.of(), List.of());

        StateMachineDefinition def = StateMachineDefinition.builder()
                .orgKey("org-1")
                .entityName("invoice")
                .name("Invoice SM")
                .description("Initial Desc")
                .stateAttributeKey("status")
                .initialStateKey("draft")
                .states(List.of(draft, approved))
                .transitions(List.of(t1))
                .build();

        assertThat(def.getOrgKey()).isEqualTo("org-1");
        assertThat(def.getEntityName()).isEqualTo("invoice");
        assertThat(def.getName()).isEqualTo("Invoice SM");
        assertThat(def.getDescription()).isEqualTo("Initial Desc");
        assertThat(def.getStateAttributeKey()).isEqualTo("status");
        assertThat(def.getInitialStateKey()).isEqualTo("draft");
        assertThat(def.getStates()).containsExactly(draft, approved);
        assertThat(def.getTransitions()).containsExactly(t1);

        def.setName("New Name");
        assertThat(def.getName()).isEqualTo("New Name");

        def.setDescription("New Desc");
        assertThat(def.getDescription()).isEqualTo("New Desc");

        def.setStateAttributeKey("state");
        assertThat(def.getStateAttributeKey()).isEqualTo("state");

        def.setInitialStateKey("approved");
        assertThat(def.getInitialStateKey()).isEqualTo("approved");

        // findState
        Optional<State> found = def.findState("draft");
        assertThat(found).isPresent().contains(draft);
        assertThat(def.findState("nonexistent")).isEmpty();

        // transitionsFrom
        List<Transition> fromDraft = def.transitionsFrom("draft");
        assertThat(fromDraft).containsExactly(t1);
        assertThat(def.transitionsFrom("approved")).isEmpty();

        // replaceTopology
        def.replaceTopology("Replaced SM", "Replaced Desc", "attr", "draft", List.of(draft), null);
        assertThat(def.getName()).isEqualTo("Replaced SM");
        assertThat(def.getTransitions()).isEmpty();

        def.replaceTopology("Replaced SM 2", "Replaced Desc 2", "attr", "draft", null, List.of(t1));
        assertThat(def.getStates()).isEmpty();
        assertThat(def.getTransitions()).containsExactly(t1);

        // lifecycle hooks
        assertThat(def.getCreatedAt()).isNull();
        assertThat(def.getUpdatedAt()).isNull();
        def.onCreate();
        assertThat(def.getCreatedAt()).isNotNull();
        assertThat(def.getUpdatedAt()).isNotNull();
        def.onUpdate();
        assertThat(def.getVersion()).isNull();
    }

    @Test
    void testStateMachineDefinitionKey() {
        StateMachineDefinitionKey key1 = new StateMachineDefinitionKey();
        key1.setOrgKey("org-1");
        key1.setEntityName("invoice");

        StateMachineDefinitionKey key2 = new StateMachineDefinitionKey("org-1", "invoice");
        StateMachineDefinitionKey key3 = new StateMachineDefinitionKey("org-2", "invoice");
        StateMachineDefinitionKey key4 = new StateMachineDefinitionKey("org-1", "order");

        assertThat(key1.getOrgKey()).isEqualTo("org-1");
        assertThat(key1.getEntityName()).isEqualTo("invoice");

        assertThat(key1)
                .isEqualTo(key2)
                .isNotEqualTo(key3)
                .isNotEqualTo(key4)
                .hasSameHashCodeAs(key2)
                .hasToString("org-1/invoice");
    }
}
