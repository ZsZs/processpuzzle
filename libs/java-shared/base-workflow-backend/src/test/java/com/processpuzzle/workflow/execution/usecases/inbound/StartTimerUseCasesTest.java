package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.TimerDefinition;
import com.processpuzzle.workflow.definition.domain.TimerType;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.execution.domain.StartTimerSchedule;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link StartTimerScheduleReconciler} and {@link FireStartTimerUseCase}. */
class StartTimerUseCasesTest {

    private static final String ORG = "acme";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private StartTimerScheduleRepository schedules;
    private WorkflowRepository workflows;
    private StartWorkflowInstanceUseCase start;
    private final List<StartTimerSchedule> rows = new ArrayList<>();
    private Clock clock;

    @BeforeEach
    void setUp() {
        schedules = mock(StartTimerScheduleRepository.class);
        workflows = mock(WorkflowRepository.class);
        start = mock(StartWorkflowInstanceUseCase.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(schedules.findByOrgKeyAndWorkflowId(ORG, "nightly")).thenAnswer(call -> List.copyOf(rows));
        when(start.startScheduled(any(), any(), any())).thenReturn(WorkflowInstance.builder().id(UUID.randomUUID()).build());
    }

    // ---------------------------------------------------------------- reconcile

    @Test
    void aNewTimedStartEventGetsAnArmedRow() {
        define(timed("every-day", TimerType.CYCLE, "R/P1D"));

        reconciler().reconcile(ORG, "nightly");

        ArgumentCaptor<StartTimerSchedule> saved = ArgumentCaptor.forClass(StartTimerSchedule.class);
        verify(schedules).save(saved.capture());
        assertThat(saved.getValue().getStartEventId()).isEqualTo("every-day");
        assertThat(saved.getValue().getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(saved.getValue().getRemainingFirings()).isNull();
        assertThat(saved.getValue().getSpecHash()).isEqualTo("CYCLE:R/P1D");
    }

    @Test
    void anUnchangedTimerKeepsItsDueTime() {
        define(timed("every-day", TimerType.CYCLE, "R/P1D"));
        rows.add(row("every-day", NOW.plusSeconds(60), "CYCLE:R/P1D"));

        reconciler().reconcile(ORG, "nightly");

        verify(schedules, never()).save(any());
        assertThat(rows.getFirst().getDueAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void aChangedTimerIsRearmed() {
        define(timed("every-day", TimerType.DATE, "2026-12-24"));
        StartTimerSchedule existing = row("every-day", NOW.plusSeconds(60), "CYCLE:R/P1D");
        rows.add(existing);

        reconciler().reconcile(ORG, "nightly");

        verify(schedules).save(existing);
        assertThat(existing.getDueAt()).isEqualTo(Instant.parse("2026-12-24T00:00:00Z"));
        assertThat(existing.getSpecHash()).isEqualTo("DATE:2026-12-24");
    }

    @Test
    void aRemovedStartEventOrWorkflowLosesItsRow() {
        StartTimerSchedule stale = row("gone", NOW, "CYCLE:R/P1D");
        rows.add(stale);
        when(workflows.findByOrgKeyAndId(ORG, "nightly")).thenReturn(Optional.empty());

        reconciler().reconcile(ORG, "nightly");

        verify(schedules).deleteAll(List.of(stale));
    }

    // ---------------------------------------------------------------- fire

    @Test
    void aDueCycleStartsTheWorkflowAndMovesOnToItsNextFiring() {
        define(timed("every-day", TimerType.CYCLE, "R3/PT1H"));
        StartTimerSchedule due = row("every-day", NOW.minusSeconds(10), "CYCLE:R3/PT1H");
        due.setRemainingFirings(2);
        when(schedules.findById(due.getId())).thenReturn(Optional.of(due));
        WorkflowInstance started = WorkflowInstance.builder().id(UUID.randomUUID()).build();
        when(start.startScheduled(ORG, "nightly", "every-day")).thenReturn(started);

        assertThat(fire().fire(due.getId())).contains(started);

        assertThat(due.getDueAt()).isEqualTo(NOW.minusSeconds(10).plus(Duration.ofHours(1)));
        assertThat(due.getRemainingFirings()).isEqualTo(1);
        verify(schedules).saveAndFlush(due);
    }

    @Test
    void missedFiringsAreSkippedRatherThanStartedInABurst() {
        define(timed("hourly", TimerType.CYCLE, "R/PT1H"));
        StartTimerSchedule due = row("hourly", NOW.minus(Duration.ofHours(5)).minusSeconds(1), "CYCLE:R/PT1H");
        due.setRemainingFirings(null);
        when(schedules.findById(due.getId())).thenReturn(Optional.of(due));

        fire().fire(due.getId());

        assertThat(due.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(1)).minusSeconds(1));
        verify(start).startScheduled(ORG, "nightly", "hourly");
    }

    @Test
    void aDateStartsOnceAndIsDone() {
        define(timed("xmas", TimerType.DATE, "2026-10-08"));
        StartTimerSchedule due = row("xmas", Instant.parse("2026-10-08T00:00:00Z"), "DATE:2026-10-08");
        due.setRemainingFirings(0);
        when(schedules.findById(due.getId())).thenReturn(Optional.of(due));

        fire().fire(due.getId());

        assertThat(due.getDueAt()).isNull();
        verify(start).startScheduled(ORG, "nightly", "xmas");
    }

    @Test
    void aRowNotDueOrWithoutItsStartEventStartsNothing() {
        define(timed("every-day", TimerType.CYCLE, "R/P1D"));
        StartTimerSchedule early = row("every-day", NOW.plusSeconds(1), "CYCLE:R/P1D");
        StartTimerSchedule orphan = row("gone", NOW.minusSeconds(1), "CYCLE:R/P1D");
        when(schedules.findById(early.getId())).thenReturn(Optional.of(early));
        when(schedules.findById(orphan.getId())).thenReturn(Optional.of(orphan));

        assertThat(fire().fire(early.getId())).isEmpty();
        assertThat(fire().fire(orphan.getId())).isEmpty();

        verify(schedules).delete(orphan);
        verifyNoInteractions(start);
    }

    // ---------------------------------------------------------------- fixtures

    private StartTimerScheduleReconciler reconciler() {
        return new StartTimerScheduleReconciler(schedules, workflows, clock);
    }

    private FireStartTimerUseCase fire() {
        return new FireStartTimerUseCase(schedules, workflows, start, clock);
    }

    private void define(StartEvent... startEvents) {
        when(workflows.findByOrgKeyAndId(ORG, "nightly")).thenReturn(Optional.of(
                Workflow.builder().orgKey(ORG).id("nightly").startEvents(List.of(startEvents)).build()));
    }

    private static StartEvent timed(String id, TimerType type, String expression) {
        return StartEvent.builder().id(id).startType(WorkflowStartConditionType.TIME_BASED_PRECONDITION)
                .timer(TimerDefinition.builder().type(type).expression(expression).build()).build();
    }

    private static StartTimerSchedule row(String startEventId, Instant dueAt, String spec) {
        return StartTimerSchedule.builder().id(UUID.randomUUID()).orgKey(ORG).workflowId("nightly")
                .startEventId(startEventId).dueAt(dueAt).specHash(spec).build();
    }
}
