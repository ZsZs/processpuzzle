package com.processpuzzle.calculation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.calculation.usecase.RunEvents;
import com.processpuzzle.core.definition.PlatformSubject;
import org.junit.jupiter.api.Test;

class CalculationPlatformSubjectConfigurationTest {

    @Test
    void declaresTheCalculationRunSubjectWithTheAttributesItsFactsCarry() {
        PlatformSubject subject = new CalculationPlatformSubjectConfiguration().calculationRunSubject();

        assertThat(subject.subjectType()).isEqualTo(RunEvents.SUBJECT_TYPE);
        assertThat(subject.attributes()).isEqualTo(RunEvents.FACT_ATTRIBUTES);
    }
}
