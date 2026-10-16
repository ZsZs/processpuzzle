package com.processpuzzle.event.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FindEventDefinitionTest {

    private final EventDefinitionRepository repository = mock(EventDefinitionRepository.class);
    private final FindEventDefinition find = new FindEventDefinition(repository);

    // Only a SYSTEM state-change event stands for a state change; a creation or a message does not.
    @Test
    void namesTheStateChangeOfASystemStateChangeEventOnly() {
        draft(EventDefinition.builder().id("OrderCancelledEvent").kind(EventKind.SYSTEM).subjectType("order")
                .action(PlatformEventAction.STATE_CHANGED).state("CANCELLED").build());
        draft(EventDefinition.builder().id("OrderCreatedEvent").kind(EventKind.SYSTEM).subjectType("order")
                .action(PlatformEventAction.CREATED).build());
        draft(EventDefinition.builder().id("InvoiceIssued").kind(EventKind.MESSAGE).build());

        assertThat(find.stateChangeOf("org-1", "OrderCancelledEvent")).contains(new EventStateChange("order", "CANCELLED"));
        assertThat(find.stateChangeOf("org-1", "OrderCreatedEvent")).isEmpty();
        assertThat(find.stateChangeOf("org-1", "InvoiceIssued")).isEmpty();
        assertThat(find.stateChangeOf("org-1", "Nope")).isEmpty();
        assertThat(find.stateChangeOf("org-1", null)).isEmpty();
    }

    private void draft(EventDefinition definition) {
        when(repository.findByOrgKeyAndIdAndChannel("org-1", definition.getId(), Channel.DRAFT)).thenReturn(Optional.of(definition));
    }
}
