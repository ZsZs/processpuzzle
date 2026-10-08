package com.processpuzzle.workflow.common;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * What base-workflow's timers run on: a {@link Clock} (UTC, replaceable by a test that needs to control
 * time) and Spring's scheduling, for {@code TimerSweepScheduler}'s polling sweep. A poll over due rows
 * rather than Quartz or a broker, as base-ai's {@code VisionJobs} does: the stage host is short on RAM.
 *
 * <p>{@code base-workflow.timers.enabled: false} turns the scheduled sweep off (timers then only fire
 * when something calls {@code TimerSweep.sweep()}) and {@code base-workflow.timers.interval} sets how
 * often it runs (default {@code PT30S}), which is also how late a timer may fire.
 * {@code @EnableScheduling} is idempotent, so another module enabling it too is harmless.
 */
@Configuration(proxyBeanMethods = false)
public class WorkflowSchedulingConfiguration {

    public static final String TIMERS_ENABLED = "base-workflow.timers.enabled";

    @Bean
    @ConditionalOnMissingBean
    Clock workflowClock() {
        return Clock.systemUTC();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = TIMERS_ENABLED, havingValue = "true", matchIfMissing = true)
    static class TimerScheduling {
    }
}
