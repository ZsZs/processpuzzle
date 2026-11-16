package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStates;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.api.StateOperationApi;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatSubjectStatesAdapterTest {

    private final StateOperationApi api = mock(StateOperationApi.class);
    private final ChatSubjectStatesAdapter adapter = new ChatSubjectStatesAdapter(api);

    @Test
    void listsTheStatesOfTheMachineThatGovernsTheEntity() {
        when(api.stateKeys("org-1", "order", Channel.DRAFT)).thenReturn(Optional.of(List.of("DRAFT", "SHIPPED")));

        assertThat(adapter.statesOf("org-1", SubjectType.ENTITY, "order", Channel.DRAFT))
                .hasValueSatisfying(states -> {
                    assertThat(states.governed()).isTrue();
                    assertThat(states.keys()).containsExactly("DRAFT", "SHIPPED");
                });
    }

    @Test
    void anEntityNoMachineGovernsAnswersNoneRatherThanUnknown() {
        when(api.stateKeys("org-1", "order", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(adapter.statesOf("org-1", SubjectType.ENTITY, "order", Channel.RELEASED)).contains(SubjectStates.NONE);
    }
}
