package com.processpuzzle.state.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordStatesAdapterTest {

    private final StateMachineDefinitionRepository repository = mock(StateMachineDefinitionRepository.class);
    private final RecordStatesAdapter adapter = new RecordStatesAdapter(repository);

    @Test
    void tracksARecordByTheAttributeOfTheReleasedMachine() {
        StateMachineDefinition machine = mock(StateMachineDefinition.class);
        when(machine.getStateAttributeKey()).thenReturn("status");
        when(machine.getInitialStateKey()).thenReturn("DRAFT");
        when(repository.findByOrgKeyAndEntityNameAndChannel("common", "race", Channel.RELEASED)).thenReturn(Optional.of(machine));

        var tracking = adapter.trackingOf("common", "race").orElseThrow();

        assertThat(tracking.stateIn(Map.of("status", "PUBLISHED"))).isEqualTo("PUBLISHED");
        assertThat(tracking.stateIn(Map.of())).as("a record holding no state is in the initial one").isEqualTo("DRAFT");
    }

    @Test
    void aTypeWithoutAMachineHasNoTracking() {
        when(repository.findByOrgKeyAndEntityNameAndChannel("common", "boat", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(adapter.trackingOf("common", "boat")).isEmpty();
    }
}
