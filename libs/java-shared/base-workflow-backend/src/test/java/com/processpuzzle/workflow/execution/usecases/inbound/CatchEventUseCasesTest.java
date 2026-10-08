package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.events.WorkflowInstanceCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** {@link WorkflowProgression} and {@link OccurCatchEventUseCase}. */
class CatchEventUseCasesTest {

    private static final String ORG = "acme";
    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    private TaskActivationService activation;
    private WorkflowInstanceRepository instances;
    private EventInstanceRepository events;
    private ResolveWorkflowUseCase resolve;
    private ApplicationEventPublisher publisher;
    private WorkflowInstance instance;
    private ResolvedWorkflow definition;

    @BeforeEach
    void setUp() {
        activation = mock(TaskActivationService.class);
        instances = mock(WorkflowInstanceRepository.class);
        events = mock(EventInstanceRepository.class);
        resolve = mock(ResolveWorkflowUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        instance = WorkflowInstance.builder().id(INSTANCE_ID).orgKey(ORG).workflowId("order")
                .status(WorkflowInstanceStatus.ACTIVE).initialContext(Map.of("orderId", "42")).build();
        EventUse issued = EventUse.builder().id("issued").eventDefinitionId("InvoiceIssued")
                .direction(EventDirection.CATCH).correlationKey("orderId")
                .payloadMapping(Map.of("invoiceNumber", "$.payload.invoiceNumber")).build();
        definition = new ResolvedWorkflow(Workflow.builder().orgKey(ORG).id("order").events(List.of(issued)).build(),
                List.of(), List.of(), List.of());
        when(instances.findByOrgKeyAndId(ORG, INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(resolve.resolveByOrgKeyAndId(ORG, "order")).thenReturn(definition);
    }

    // ---------------------------------------------------------------- progression

    @Test
    void progressionActivatesThenClosesAnInstanceWithNothingLeft() {
        when(instances.lockForCloseOut(ORG, INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(activation.allTerminal(ORG, definition, INSTANCE_ID)).thenReturn(true);

        boolean completed = new WorkflowProgression(activation, instances, publisher).advance(ORG, definition, instance, Map.of());

        assertThat(completed).isTrue();
        verify(activation).activateEligibleTasks(ORG, definition, INSTANCE_ID, Map.of());
        assertThat(instance.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
        assertThat(instance.getCompletedAt()).isNotNull();
        verify(publisher).publishEvent(new WorkflowInstanceCompletedEvent(ORG, INSTANCE_ID, "order"));
    }

    @Test
    void progressionLeavesAnInstanceOpenWhileSomethingCanStillMove() {
        when(instances.lockForCloseOut(ORG, INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(activation.allTerminal(ORG, definition, INSTANCE_ID)).thenReturn(false);

        assertThat(new WorkflowProgression(activation, instances, publisher).advance(ORG, definition, instance, Map.of())).isFalse();
        assertThat(instance.getStatus()).isEqualTo(WorkflowInstanceStatus.ACTIVE);
        verify(instances, never()).save(any());
    }

    /** The locked re-read is what counts: another transaction may have closed or cancelled it meanwhile. */
    @Test
    void progressionDoesNotCloseAnInstanceTheLockedReadShowsFinished() {
        WorkflowInstance cancelled = WorkflowInstance.builder().id(INSTANCE_ID).orgKey(ORG)
                .status(WorkflowInstanceStatus.CANCELLED).build();
        when(instances.lockForCloseOut(ORG, INSTANCE_ID)).thenReturn(Optional.of(cancelled));

        assertThat(new WorkflowProgression(activation, instances, publisher).advance(ORG, definition, instance, Map.of())).isFalse();
        verify(activation, never()).allTerminal(any(), any(), any());
        verifyNoInteractions(publisher);
    }

    // ---------------------------------------------------------------- occur

    @Test
    void occurRecordsTheContributionAndMovesTheInstanceOn() {
        EventInstance waiting = waiting();
        when(events.findByOrgKeyAndId(ORG, EVENT_ID)).thenReturn(Optional.of(waiting));
        when(events.findByOrgKeyAndWorkflowInstanceId(ORG, INSTANCE_ID)).thenReturn(List.of(waiting));
        WorkflowProgression progression = mock(WorkflowProgression.class);
        UUID occurrence = UUID.randomUUID();

        boolean delivered = useCase(progression).occur(ORG, EVENT_ID, message(occurrence));

        assertThat(delivered).isTrue();
        assertThat(waiting.getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        assertThat(waiting.getOccurrenceId()).isEqualTo(occurrence);
        assertThat(waiting.getOccurredAt()).isNotNull();
        assertThat(waiting.getPayload()).containsEntry("invoiceNumber", "I-1");
        assertThat(waiting.getContextContribution()).containsExactlyEntriesOf(Map.of("invoiceNumber", "I-1"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(progression).advance(eq(ORG), eq(definition), eq(instance), context.capture());
        assertThat(context.getValue()).containsEntry("orderId", "42").containsEntry("invoiceNumber", "I-1");
    }

    @Test
    void occurIgnoresACatchThatNoLongerWaits() {
        EventInstance occurred = waiting();
        occurred.setStatus(EventInstanceStatus.OCCURRED);
        when(events.findByOrgKeyAndId(ORG, EVENT_ID)).thenReturn(Optional.of(occurred));
        WorkflowProgression progression = mock(WorkflowProgression.class);

        assertThat(useCase(progression).occur(ORG, EVENT_ID, message(UUID.randomUUID()))).isFalse();
        assertThat(useCase(progression).occur(ORG, UUID.randomUUID(), message(UUID.randomUUID()))).isFalse();
        verifyNoInteractions(progression);
    }

    @Test
    void occurIgnoresACatchOfAnInstanceNoLongerActive() {
        when(events.findByOrgKeyAndId(ORG, EVENT_ID)).thenReturn(Optional.of(waiting()));
        instance.setStatus(WorkflowInstanceStatus.CANCELLED);
        WorkflowProgression progression = mock(WorkflowProgression.class);

        assertThat(useCase(progression).occur(ORG, EVENT_ID, message(UUID.randomUUID()))).isFalse();
        verifyNoInteractions(progression);
    }

    // ---------------------------------------------------------------- fixtures

    private OccurCatchEventUseCase useCase(WorkflowProgression progression) {
        return new OccurCatchEventUseCase(events, instances, mock(TaskInstanceRepository.class), resolve, progression);
    }

    private static EventInstance waiting() {
        return EventInstance.builder().id(EVENT_ID).orgKey(ORG).workflowInstanceId(INSTANCE_ID).eventUseId("issued")
                .eventDefinitionId("InvoiceIssued").direction(EventDirection.CATCH).status(EventInstanceStatus.WAITING)
                .correlationValue("42").build();
    }

    private static DefinedEventOccurred message(UUID occurrence) {
        return new DefinedEventOccurred(ORG, "InvoiceIssued", "order", "42", Map.of("invoiceNumber", "I-1"),
                Instant.now(), CatalogEventKind.MESSAGE, occurrence, "42", UUID.randomUUID());
    }
}
