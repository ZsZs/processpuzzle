package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.TimerDefinition;
import com.processpuzzle.workflow.definition.domain.TimerType;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.events.TaskInterruptedEvent;
import com.processpuzzle.workflow.execution.events.TimerFiredEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** {@link FireTimerUseCase}, with a real {@link CatchOccurrence} over a mocked progression. */
class FireTimerUseCaseTest {

    private static final String ORG = "acme";
    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private EventInstanceRepository events;
    private TaskInstanceRepository tasks;
    private ResolveWorkflowUseCase resolve;
    private WorkflowProgression progression;
    private ApplicationEventPublisher publisher;
    private WorkflowInstance instance;
    private FireTimerUseCase useCase;

    @BeforeEach
    void setUp() {
        events = mock(EventInstanceRepository.class);
        tasks = mock(TaskInstanceRepository.class);
        resolve = mock(ResolveWorkflowUseCase.class);
        progression = mock(WorkflowProgression.class);
        publisher = mock(ApplicationEventPublisher.class);
        WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
        instance = WorkflowInstance.builder().id(INSTANCE_ID).orgKey(ORG).workflowId("invoice")
                .status(WorkflowInstanceStatus.ACTIVE).build();
        when(instances.findByOrgKeyAndId(ORG, INSTANCE_ID)).thenReturn(Optional.of(instance));
        useCase = new FireTimerUseCase(events, instances, tasks, resolve,
                new CatchOccurrence(events, tasks, progression, publisher), publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anInterruptingBoundaryFiresOnceAndCancelsItsTask() {
        TaskInstance issue = activeTask("issue");
        EventInstance overdue = waitingTimer("overdue", NOW.minusSeconds(5), 0);
        define(boundary("overdue", "issue", true, TimerType.DURATION, "PT1H"));

        assertThat(useCase.fire(ORG, EVENT_ID)).isTrue();

        assertThat(overdue.getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        assertThat(overdue.getFireCount()).isEqualTo(1);
        assertThat(overdue.getDueAt()).isNull();
        assertThat(overdue.getOccurrenceId()).isEqualTo(EVENT_ID);
        assertThat(issue.getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
        assertThat(issue.getCancelReason()).isEqualTo("interrupted by overdue");
        verify(publisher).publishEvent(new TimerFiredEvent(ORG, INSTANCE_ID, EVENT_ID, "overdue", 1, NOW.minusSeconds(5), NOW));
        verify(publisher).publishEvent(new TaskInterruptedEvent(ORG, INSTANCE_ID, issue.getId(), "issue", "overdue"));
        verify(progression).advance(eq(ORG), any(), eq(instance), any());
    }

    @Test
    void aCycleIsRearmedWhileFiringsAreLeftAndLeavesItsTaskRunning() {
        TaskInstance approve = activeTask("approve");
        Instant due = NOW.minusSeconds(1);
        EventInstance reminder = waitingTimer("reminder", due, 1);
        define(boundary("reminder", "approve", false, TimerType.CYCLE, "R2/PT5M"));

        assertThat(useCase.fire(ORG, EVENT_ID)).isTrue();

        assertThat(reminder.getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        assertThat(reminder.getDueAt()).isEqualTo(due.plus(Duration.ofMinutes(5)));
        assertThat(reminder.getRemainingFirings()).isZero();
        assertThat(approve.getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        verify(progression).advance(eq(ORG), any(), eq(instance), any());
    }

    @Test
    void anExhaustedCycleFiresForTheLastTimeWithoutMovingTheInstanceAgain() {
        activeTask("approve");
        EventInstance reminder = waitingTimer("reminder", NOW, 0);
        reminder.setStatus(EventInstanceStatus.OCCURRED);
        reminder.setFireCount(1);
        define(boundary("reminder", "approve", false, TimerType.CYCLE, "R2/PT5M"));

        assertThat(useCase.fire(ORG, EVENT_ID)).isTrue();

        assertThat(reminder.getFireCount()).isEqualTo(2);
        assertThat(reminder.getDueAt()).isNull();
        verify(events).save(reminder);
        verifyNoInteractions(progression);
        verify(publisher).publishEvent(new TimerFiredEvent(ORG, INSTANCE_ID, EVENT_ID, "reminder", 2, NOW, NOW));
    }

    @Test
    void aTimerNotYetDueDoesNotFire() {
        EventInstance wait = waitingTimer("wait", NOW.plusSeconds(1), 0);
        define(timerCatch("wait"));

        assertThat(useCase.fire(ORG, EVENT_ID)).isFalse();

        assertThat(wait.getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        verifyNoInteractions(publisher, progression);
    }

    @Test
    void aStaleDueDateOfAnEndedInstanceIsCleared() {
        EventInstance wait = waitingTimer("wait", NOW.minusSeconds(60), 0);
        define(timerCatch("wait"));
        instance.setStatus(WorkflowInstanceStatus.CANCELLED);

        assertThat(useCase.fire(ORG, EVENT_ID)).isFalse();

        assertThat(wait.getDueAt()).isNull();
        assertThat(wait.getStatus()).isEqualTo(EventInstanceStatus.CANCELLED);
        verify(events).save(wait);
        verifyNoInteractions(publisher, progression);
    }

    @Test
    void aBoundaryWhoseTaskIsNoLongerActiveIsCancelledInsteadOfFired() {
        TaskInstance issue = activeTask("issue");
        issue.setStatus(TaskInstanceStatus.COMPLETED);
        EventInstance overdue = waitingTimer("overdue", NOW.minusSeconds(5), 0);
        define(boundary("overdue", "issue", true, TimerType.DURATION, "PT1H"));

        assertThat(useCase.fire(ORG, EVENT_ID)).isFalse();

        assertThat(overdue.getStatus()).isEqualTo(EventInstanceStatus.CANCELLED);
        assertThat(overdue.getDueAt()).isNull();
        verify(tasks, never()).save(any());
        verifyNoInteractions(progression);
    }

    @Test
    void anIntermediateTimerOccursAndMovesTheInstanceOn() {
        EventInstance wait = waitingTimer("wait", NOW.minusSeconds(5), 0);
        define(timerCatch("wait"));

        assertThat(useCase.fire(ORG, EVENT_ID)).isTrue();

        assertThat(wait.getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        assertThat(wait.getDueAt()).isNull();
        assertThat(wait.getContextContribution()).isEmpty();
        verify(progression).advance(eq(ORG), any(), eq(instance), any());
    }

    // ---------------------------------------------------------------- fixtures

    private void define(EventUse use) {
        ResolvedWorkflow definition = new ResolvedWorkflow(
                Workflow.builder().orgKey(ORG).id("invoice").events(List.of(use)).build(), List.of(), List.of(), List.of());
        when(resolve.resolveByOrgKeyAndId(ORG, "invoice")).thenReturn(definition);
    }

    private TaskInstance activeTask(String id) {
        TaskInstance task = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(INSTANCE_ID)
                .taskDefinitionId(id).name(id).status(TaskInstanceStatus.ACTIVE).contextContribution(Map.of()).build();
        when(tasks.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, INSTANCE_ID, id)).thenReturn(Optional.of(task));
        return task;
    }

    private EventInstance waitingTimer(String useId, Instant dueAt, Integer remaining) {
        EventInstance event = EventInstance.builder().id(EVENT_ID).orgKey(ORG).workflowInstanceId(INSTANCE_ID)
                .eventUseId(useId).direction(EventDirection.CATCH).status(EventInstanceStatus.WAITING)
                .dueAt(dueAt).remainingFirings(remaining).fireCount(0).build();
        when(events.findByOrgKeyAndId(ORG, EVENT_ID)).thenReturn(Optional.of(event));
        return event;
    }

    private static EventUse timerCatch(String id) {
        return EventUse.builder().id(id).direction(EventDirection.CATCH)
                .timer(TimerDefinition.builder().type(TimerType.DURATION).expression("PT1H").build()).build();
    }

    private static EventUse boundary(String id, String task, boolean interrupting, TimerType type, String expression) {
        return EventUse.builder().id(id).direction(EventDirection.CATCH).attachedTo(task).interrupting(interrupting)
                .timer(TimerDefinition.builder().type(type).expression(expression).build()).build();
    }
}
