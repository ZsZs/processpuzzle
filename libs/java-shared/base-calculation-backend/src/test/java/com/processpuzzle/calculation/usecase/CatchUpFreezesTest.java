package com.processpuzzle.calculation.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.engine.Calculations;
import com.processpuzzle.calculation.port.SubjectStatePort;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CatchUpFreezesTest {

    private FindCalculationDefinition definitions;
    private RunCalculation runCalculation;
    private FreezeRun freezeRun;
    private SubjectStatePort states;
    private CatchUpFreezes catchUp;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        definitions = mock(FindCalculationDefinition.class);
        runCalculation = mock(RunCalculation.class);
        freezeRun = mock(FreezeRun.class);
        states = mock(SubjectStatePort.class);
        ObjectProvider<SubjectStatePort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(states);
        catchUp = new CatchUpFreezes(definitions, runCalculation, freezeRun, provider);
    }

    private void released(String id, String source) {
        when(definitions.find("org", id, Channel.RELEASED))
                .thenReturn(CalculationDefinition.builder().orgKey("org").id(id).source(source).build());
    }

    private static CalculationRun run(RunStatus status) {
        return CalculationRun.builder().id(UUID.randomUUID()).status(status).build();
    }

    @Test
    void computesAndFreezesTheSubjectsAlreadyInTheFreezeStateThatAreNotFrozen() {
        released("order-total", Calculations.source("order-total"));
        when(states.objectsInState("org", "order", "CONFIRMED")).thenReturn(List.of("o-1", "o-2", "o-3"));
        when(runCalculation.isFrozen("org", "order-total", "o-2")).thenReturn(true);
        CalculationRun completed = run(RunStatus.COMPLETED);
        when(runCalculation.run("org", "order-total", Channel.RELEASED, Map.of("order", "o-1"), RunTrigger.FREEZE_STATE))
                .thenReturn(completed);
        when(runCalculation.run("org", "order-total", Channel.RELEASED, Map.of("order", "o-3"), RunTrigger.FREEZE_STATE))
                .thenReturn(run(RunStatus.FAILED));

        assertThat(catchUp.execute("org", List.of("order-total"))).isEqualTo(1);

        verify(freezeRun).freeze("org", completed.getId());
        verify(runCalculation, never()).run("org", "order-total", Channel.RELEASED, Map.of("order", "o-2"), RunTrigger.FREEZE_STATE);
    }

    @Test
    void oneSubjectFailingDoesNotStopTheOthers() {
        released("order-total", Calculations.source("order-total"));
        when(states.objectsInState("org", "order", "CONFIRMED")).thenReturn(List.of("o-1", "o-2"));
        CalculationRun completed = run(RunStatus.COMPLETED);
        when(runCalculation.run("org", "order-total", Channel.RELEASED, Map.of("order", "o-1"), RunTrigger.FREEZE_STATE))
                .thenThrow(new IllegalStateException("boom"));
        when(runCalculation.run("org", "order-total", Channel.RELEASED, Map.of("order", "o-2"), RunTrigger.FREEZE_STATE))
                .thenReturn(completed);

        assertThat(catchUp.execute("org", List.of("order-total"))).isEqualTo(1);
    }

    @Test
    void aStateLookupThatFailsCatchesNothingUpRatherThanFailingTheRelease() {
        released("order-total", Calculations.source("order-total"));
        when(states.objectsInState("org", "order", "CONFIRMED")).thenThrow(new IllegalArgumentException("no such attribute"));

        assertThat(catchUp.execute("org", List.of("order-total"))).isZero();
        verifyNoInteractions(runCalculation);
    }

    @Test
    void ignoresCalculationsWithoutAFreezeStateAndOnesNoLongerReleased() {
        released("series", Calculations.source("sail-series-ranking"));
        when(definitions.find("org", "gone", Channel.RELEASED)).thenThrow(new CalculationNotFoundException("org", "gone"));

        assertThat(catchUp.execute("org", List.of("series", "gone"))).isZero();
        verifyNoInteractions(states, runCalculation);
    }

    @Test
    @SuppressWarnings("unchecked")
    void catchesNothingUpWithoutAStatePort() {
        ObjectProvider<SubjectStatePort> none = mock(ObjectProvider.class);
        CatchUpFreezes withoutStates = new CatchUpFreezes(definitions, runCalculation, freezeRun, none);

        assertThat(withoutStates.execute("org", List.of("order-total"))).isZero();
        verifyNoInteractions(definitions, runCalculation);
        verify(none).getIfAvailable();
        verifyNoInteractions(freezeRun);
    }
}
