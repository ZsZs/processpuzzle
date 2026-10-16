package com.processpuzzle.workflow.definition.adapters.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.workflow.definition.usecases.outbound.NoStateMachineCatalogPort;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort.Machine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** The release reads base-state's draft machines through this adapter, and through nothing when base-state is absent. */
class StateMachineCatalogAdapterTest {

    private final StateMachineDefinitionRepository repository = mock(StateMachineDefinitionRepository.class);

    @Test
    void readsTheDraftMachineAsItsStatesAndTransitions() {
        when(repository.findByOrgKeyAndEntityNameAndChannel("org-1", "order", Channel.DRAFT)).thenReturn(Optional.of(StateMachineDefinition.builder()
                .orgKey("org-1").entityName("order").name("Order").initialStateKey("DRAFT")
                .states(List.of(state("DRAFT"), state("CANCELLED")))
                .transitions(List.of(new Transition("cancel-draft", "Cancel (from Draft)", "DRAFT", "CANCELLED", "cancel", List.of(), List.of())))
                .build()));

        Optional<Machine> machine = new BaseStateMachineCatalogAdapter(repository).draftOf("org-1", "order");

        assertThat(machine).hasValueSatisfying(order -> {
            assertThat(order.entityName()).isEqualTo("order");
            assertThat(order.stateKeys()).containsExactly("DRAFT", "CANCELLED");
            assertThat(order.hasState("draft")).isTrue();
            assertThat(order.transitionsFrom("DRAFT")).containsExactly(
                    new StateMachineCatalogPort.Transition("cancel-draft", "Cancel (from Draft)", "DRAFT", "CANCELLED"));
            assertThat(order.transitionsFrom("CANCELLED")).isEmpty();
        });
    }

    @Test
    void knowsNoMachineForAnUnknownOrBlankId() {
        BaseStateMachineCatalogAdapter adapter = new BaseStateMachineCatalogAdapter(repository);

        assertThat(adapter.draftOf("org-1", "invoice")).isEmpty();
        assertThat(adapter.draftOf("org-1", " ")).isEmpty();
        assertThat(adapter.draftOf("org-1", null)).isEmpty();
    }

    @Test
    void bindsBaseStateWhenItIsDeployed_andNothingOtherwise() {
        StateMachineCatalogConfiguration configuration = new StateMachineCatalogConfiguration();

        assertThat(configuration.stateMachineCatalogPort(provider(repository))).isInstanceOf(BaseStateMachineCatalogAdapter.class);
        StateMachineCatalogPort none = configuration.stateMachineCatalogPort(provider(null));
        assertThat(none).isInstanceOf(NoStateMachineCatalogPort.class);
        assertThat(none.draftOf("org-1", "order")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StateMachineDefinitionRepository> provider(StateMachineDefinitionRepository repository) {
        ObjectProvider<StateMachineDefinitionRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        return provider;
    }

    private static State state(String key) {
        return new State(key, key, null, false, false, Map.of());
    }
}
