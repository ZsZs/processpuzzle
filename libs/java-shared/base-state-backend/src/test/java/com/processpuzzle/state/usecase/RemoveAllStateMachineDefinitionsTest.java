package com.processpuzzle.state.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RemoveAllStateMachineDefinitionsTest {

    @Test
    void deletesEveryMachineOfTheOrganizationAndItsDiagrams() {
        StateMachineDefinitionRepository repository = mock(StateMachineDefinitionRepository.class);
        DiagramDefinitionRepository diagrams = mock(DiagramDefinitionRepository.class);
        StateMachineDefinition machine = mock(StateMachineDefinition.class);
        when(machine.getEntityName()).thenReturn("Order");
        when(repository.findByOrgKey("acme")).thenReturn(List.of(machine));
        ImportStateMachineDefinitions useCase =
                new ImportStateMachineDefinitions(repository, diagrams, mock(StateMachineTopologyValidator.class));

        assertThat(useCase.removeAll("acme")).containsExactly("Order");

        InOrder order = inOrder(diagrams, repository);
        order.verify(diagrams).deleteByOrgKey("acme");
        order.verify(repository).deleteAll(List.of(machine));
        order.verify(repository).flush();
    }
}
