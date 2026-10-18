package com.processpuzzle.calculation.adapter.outbound;

import com.processpuzzle.calculation.usecase.RunEvents;
import com.processpuzzle.core.definition.PlatformSubject;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@code calculation-run} — the subject of the facts {@link RunEvents} publishes — so the event catalog can
 * name a completed, failed or frozen run although no entity type is called that.
 */
@Configuration(proxyBeanMethods = false)
public class CalculationPlatformSubjectConfiguration {

    @Bean
    PlatformSubject calculationRunSubject() {
        return new PlatformSubject(RunEvents.SUBJECT_TYPE, RunEvents.FACT_ATTRIBUTES);
    }
}
