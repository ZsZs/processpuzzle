package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.StartTimerSchedule;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.FireStartTimerUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FireTimerUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimerSweepTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private EventInstanceRepository events;
    private StartTimerScheduleRepository schedules;
    private FireTimerUseCase fireTimer;
    private FireStartTimerUseCase fireStartTimer;
    private TimerSweep sweep;

    @BeforeEach
    void setUp() {
        events = mock(EventInstanceRepository.class);
        schedules = mock(StartTimerScheduleRepository.class);
        fireTimer = mock(FireTimerUseCase.class);
        fireStartTimer = mock(FireStartTimerUseCase.class);
        when(schedules.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW)).thenReturn(List.of());
        sweep = new TimerSweep(events, schedules, fireTimer, fireStartTimer, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firesEveryDueTimerAndStartTimer() {
        List<EventInstance> due = timers(3);
        StartTimerSchedule schedule = StartTimerSchedule.builder().id(UUID.randomUUID()).build();
        when(events.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW)).thenReturn(due);
        when(schedules.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW)).thenReturn(List.of(schedule));
        when(fireTimer.fire(eq("acme"), any())).thenReturn(true);
        when(fireStartTimer.fire(schedule.getId())).thenReturn(Optional.of(new WorkflowInstance()));

        assertThat(sweep.sweep()).isEqualTo(4);

        due.forEach(timer -> verify(fireTimer).fire("acme", timer.getId()));
    }

    @Test
    void aFailingTimerIsLoggedAndTheOthersStillFire() {
        List<EventInstance> due = timers(2);
        when(events.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW)).thenReturn(due);
        when(fireTimer.fire("acme", due.get(0).getId())).thenThrow(new IllegalStateException("locked"));
        when(fireTimer.fire("acme", due.get(1).getId())).thenReturn(true);

        assertThat(sweep.sweep()).isEqualTo(1);
    }

    @Test
    void aFullPageThatFiredIsFollowedByTheNext() {
        when(events.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW))
                .thenReturn(timers(TimerSweep.PAGE_SIZE), timers(3));
        when(fireTimer.fire(eq("acme"), any())).thenReturn(true);

        assertThat(sweep.sweep()).isEqualTo(TimerSweep.PAGE_SIZE + 3);
        verify(events, times(2)).findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW);
    }

    @Test
    void aFullPageInWhichNothingFiredIsNotReadAgain() {
        when(events.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW)).thenReturn(timers(TimerSweep.PAGE_SIZE));
        when(fireTimer.fire(eq("acme"), any())).thenReturn(false);

        assertThat(sweep.sweep()).isZero();
        verify(events, times(1)).findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW);
    }

    @Test
    void pagingStopsAfterTheLastPageAllowed() {
        when(events.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(NOW)).thenAnswer(call -> timers(TimerSweep.PAGE_SIZE));
        when(fireTimer.fire(eq("acme"), any())).thenReturn(true);

        assertThat(sweep.sweep()).isEqualTo(TimerSweep.PAGE_SIZE * TimerSweep.MAX_PAGES);
    }

    private static List<EventInstance> timers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> EventInstance.builder().id(UUID.randomUUID()).orgKey("acme").eventUseId("t" + i)
                        .workflowInstanceId(UUID.randomUUID()).dueAt(NOW).build())
                .toList();
    }
}
