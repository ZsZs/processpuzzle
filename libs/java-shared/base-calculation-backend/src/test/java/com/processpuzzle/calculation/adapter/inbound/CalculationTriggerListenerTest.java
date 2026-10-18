package com.processpuzzle.calculation.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.domain.definition.TriggerSpec;
import com.processpuzzle.calculation.engine.Calculations;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.FreezeRun;
import com.processpuzzle.calculation.usecase.RunCalculation;
import com.processpuzzle.calculation.usecase.exception.SubjectFrozenException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculationTriggerListenerTest {

    private FindCalculationDefinition definitions;
    private RunCalculation runCalculation;
    private FreezeRun freezeRun;
    private CalculationTriggerListener listener;

    @BeforeEach
    void setUp() {
        definitions = mock(FindCalculationDefinition.class);
        runCalculation = mock(RunCalculation.class);
        freezeRun = mock(FreezeRun.class);
        listener = new CalculationTriggerListener(definitions, runCalculation, freezeRun);
        CalculationDefinition orderTotal = CalculationDefinition.builder().orgKey("org").id("order-total")
                .source(Calculations.source("order-total")).build();
        when(definitions.findAll("org", Channel.RELEASED)).thenReturn(List.of(orderTotal));
    }

    private static PlatformEvent changed(PlatformEventAction action) {
        return PlatformEvent.of("org", "order", "o-1", action, Map.of(), Instant.now());
    }

    private static CalculationRun run(RunStatus status) {
        return CalculationRun.builder().id(UUID.randomUUID()).status(status).build();
    }

    @Test
    void recomputesAChangedOrder() {
        when(runCalculation.run(any(), any(), any(), any(), any())).thenReturn(run(RunStatus.COMPLETED));

        listener.on(changed(PlatformEventAction.UPDATED));
        listener.on(changed(PlatformEventAction.CREATED));

        verify(runCalculation, org.mockito.Mockito.times(2))
                .run("org", "order-total", Channel.RELEASED, Map.of("order", "o-1"), RunTrigger.ON_CHANGE);
        verifyNoInteractions(freezeRun);
    }

    @Test
    void recomputesAndFreezesOnTheFreezeState() {
        CalculationRun completed = run(RunStatus.COMPLETED);
        when(runCalculation.run(any(), any(), any(), any(), eq(RunTrigger.FREEZE_STATE))).thenReturn(completed);

        listener.on(PlatformEvent.stateChanged("org", "order", "o-1", "CONFIRMED", Map.of(), Instant.now()));

        verify(freezeRun).freeze("org", completed.getId());
    }

    @Test
    void doesNotFreezeAFailedRun() {
        when(runCalculation.run(any(), any(), any(), any(), any())).thenReturn(run(RunStatus.FAILED));

        listener.on(PlatformEvent.stateChanged("org", "order", "o-1", "CONFIRMED", Map.of(), Instant.now()));

        verify(freezeRun, never()).freeze(any(), any());
    }

    @Test
    void ignoresOtherFactsAndSurvivesFailures() {
        listener.on(PlatformEvent.stateChanged("org", "order", "o-1", "SHIPPED", Map.of(), Instant.now()));
        listener.on(changed(PlatformEventAction.DELETED));
        listener.on(PlatformEvent.of("org", "customer", "c-1", PlatformEventAction.UPDATED, Map.of(), Instant.now()));
        listener.on(PlatformEvent.stateChanged("org", "calculation-run", "r-1", "COMPLETED", Map.of(), Instant.now()));
        verifyNoInteractions(runCalculation);

        org.mockito.Mockito.doThrow(new SubjectFrozenException("order-total", "order", "o-1"))
                .doThrow(new IllegalStateException("boom")).when(runCalculation).run(any(), any(), any(), any(), any());
        listener.on(changed(PlatformEventAction.UPDATED));
        listener.on(changed(PlatformEventAction.UPDATED));
    }

    @Test
    void namesWhatAFactIsToATrigger() {
        TriggerSpec trigger = CalculationParser.parse(Calculations.source("sail-series-ranking")).model().trigger();

        assertThat(CalculationTriggerListener.kindOf(trigger,
                PlatformEvent.stateChanged("org", "series", "s", "FINISHED", Map.of(), Instant.now()))).isEqualTo(RunTrigger.STATE);
        assertThat(CalculationTriggerListener.kindOf(trigger,
                PlatformEvent.of("org", "series", "s", PlatformEventAction.UPDATED, Map.of(), Instant.now()))).isNull();
    }
}
