package com.processpuzzle.baseprocess.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseProcessTest {

    @Test
    void constructor_shouldDefaultStepsToEmptyList() {
        BaseProcess process = new BaseProcess("onboarding");

        assertEquals("onboarding", process.getName());
        assertTrue(process.getSteps().isEmpty());
        assertEquals(0, process.stepCount());
    }

    @Test
    void stepCount_shouldReportTheNumberOfStepsProvided() {
        BaseProcess process = new BaseProcess("onboarding", List.of("greet", "verify", "activate"));

        assertEquals(3, process.stepCount());
    }
}
