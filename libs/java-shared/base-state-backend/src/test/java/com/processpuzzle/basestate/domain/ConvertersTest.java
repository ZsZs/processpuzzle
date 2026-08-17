package com.processpuzzle.basestate.domain;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConvertersTest {

    private final StatesConverter statesConverter = new StatesConverter();
    private final TransitionsConverter transitionsConverter = new TransitionsConverter();

    @Test
    void statesConverter_convertToDatabaseColumn() {
        assertThat(statesConverter.convertToDatabaseColumn(null)).isEqualTo("[]");

        State state = new State("draft", "Draft", "Desc", false, true, Map.of("k", "v"));
        String json = statesConverter.convertToDatabaseColumn(List.of(state));
        assertThat(json).contains("\"key\":\"draft\"").contains("\"isLocked\":true");
    }

    @Test
    void statesConverter_convertToEntityAttribute() {
        assertThat(statesConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(statesConverter.convertToEntityAttribute("")).isEmpty();
        assertThat(statesConverter.convertToEntityAttribute("   ")).isEmpty();

        String json = "[{\"key\":\"draft\",\"name\":\"Draft\",\"description\":\"Desc\",\"isFinal\":false,\"isLocked\":true,\"metadata\":{\"k\":\"v\"}}]";
        List<State> states = statesConverter.convertToEntityAttribute(json);
        assertThat(states).hasSize(1);
        assertThat(states.get(0).key()).isEqualTo("draft");
        assertThat(states.get(0).name()).isEqualTo("Draft");
        assertThat(states.get(0).isLocked()).isTrue();
        assertThat(states.get(0).metadata()).isEqualTo(Map.of("k", "v"));

        assertThatThrownBy(() -> statesConverter.convertToEntityAttribute("{invalid json"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void transitionsConverter_convertToDatabaseColumn() {
        assertThat(transitionsConverter.convertToDatabaseColumn(null)).isEqualTo("[]");

        Transition transition = new Transition(
                "submit", "Submit", "draft", "submitted", "SUBMIT",
                List.of(new GuardRef("g1", Map.of("a", "b"))),
                List.of(new ActionRef("a1", Map.of("c", "d")))
        );
        String json = transitionsConverter.convertToDatabaseColumn(List.of(transition));
        assertThat(json).contains("\"key\":\"submit\"").contains("\"sourceStateKey\":\"draft\"");
    }

    @Test
    void transitionsConverter_convertToEntityAttribute() {
        assertThat(transitionsConverter.convertToEntityAttribute(null)).isEmpty();
        assertThat(transitionsConverter.convertToEntityAttribute("")).isEmpty();
        assertThat(transitionsConverter.convertToEntityAttribute("   ")).isEmpty();

        String json = "[{\"key\":\"submit\",\"name\":\"Submit\",\"sourceStateKey\":\"draft\",\"targetStateKey\":\"submitted\",\"triggerKey\":\"SUBMIT\",\"guards\":[{\"beanName\":\"g1\",\"params\":{\"a\":\"b\"}}],\"actions\":[{\"beanName\":\"a1\",\"params\":{\"c\":\"d\"}}]}]";
        List<Transition> transitions = transitionsConverter.convertToEntityAttribute(json);
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).key()).isEqualTo("submit");
        assertThat(transitions.get(0).triggerKey()).isEqualTo("SUBMIT");
        assertThat(transitions.get(0).guards().get(0).beanName()).isEqualTo("g1");

        assertThatThrownBy(() -> transitionsConverter.convertToEntityAttribute("{invalid json"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
