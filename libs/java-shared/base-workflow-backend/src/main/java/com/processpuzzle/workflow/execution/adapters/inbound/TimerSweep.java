package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.usecases.inbound.FireStartTimerUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FireTimerUseCase;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fires what is due: the timer catches of running instances ({@link FireTimerUseCase}) and the start
 * timers of TIME_BASED_PRECONDITION start events ({@link FireStartTimerUseCase}), across organizations.
 *
 * <p>Reads a page of due rows at a time, earliest first, and fires each in a transaction of its own; a
 * row that fails is logged and stays due, so the next sweep retries it. Another page follows while the
 * last one was full and fired something; a full page in which nothing fired would only be read again.
 *
 * <p>Called by {@code TimerSweepScheduler} every {@code base-workflow.timers.interval}, and directly by
 * tests. Reads outside a transaction; each fire re-checks what it read.
 */
@Component
public class TimerSweep {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 10;
    private static final Logger LOG = LoggerFactory.getLogger(TimerSweep.class);

    private final EventInstanceRepository eventInstanceRepository;
    private final StartTimerScheduleRepository scheduleRepository;
    private final FireTimerUseCase fireTimer;
    private final FireStartTimerUseCase fireStartTimer;
    private final Clock clock;

    public TimerSweep(EventInstanceRepository eventInstanceRepository, StartTimerScheduleRepository scheduleRepository,
                      FireTimerUseCase fireTimer, FireStartTimerUseCase fireStartTimer, Clock clock) {
        this.eventInstanceRepository = eventInstanceRepository;
        this.scheduleRepository = scheduleRepository;
        this.fireTimer = fireTimer;
        this.fireStartTimer = fireStartTimer;
        this.clock = clock;
    }

    /** @return how many timers fired, scheduled starts included */
    public int sweep() {
        int fired = sweepPages(
                () -> eventInstanceRepository.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(clock.instant()),
                timer -> fireTimer.fire(timer.getOrgKey(), timer.getId()),
                timer -> "timer %s of workflow instance %s".formatted(timer.getEventUseId(), timer.getWorkflowInstanceId()));
        fired += sweepPages(
                () -> scheduleRepository.findTop100ByDueAtLessThanEqualOrderByDueAtAsc(clock.instant()),
                schedule -> fireStartTimer.fire(schedule.getId()).isPresent(),
                schedule -> "start event %s of workflow %s".formatted(schedule.getStartEventId(), schedule.getWorkflowId()));
        return fired;
    }

    private <T> int sweepPages(Supplier<List<T>> nextPage, Predicate<T> fire, Function<T, String> describe) {
        int fired = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            List<T> due = nextPage.get();
            int firedInPage = 0;
            for (T row : due) {
                try {
                    if (fire.test(row)) {
                        firedInPage++;
                    }
                } catch (RuntimeException e) {
                    LOG.warn("Firing {} failed; the next sweep retries it.", describe.apply(row), e);
                }
            }
            fired += firedInPage;
            if (due.size() < PAGE_SIZE || firedInPage == 0) {
                break;
            }
        }
        return fired;
    }
}
