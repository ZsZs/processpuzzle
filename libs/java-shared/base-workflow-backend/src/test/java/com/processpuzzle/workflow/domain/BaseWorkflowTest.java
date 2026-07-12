package com.processpuzzle.workflow.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseWorkflowTest {

    @Test
    void constructor_shouldDefaultStepsToEmptyList() {
        BaseWorkflow process = new BaseWorkflow("onboarding");

        assertEquals("onboarding", process.getName());
        assertTrue(process.getSteps().isEmpty());
        assertEquals(0, process.stepCount());
    }

    @Test
    void stepCount_shouldReportTheNumberOfStepsProvided() {
        BaseWorkflow process = new BaseWorkflow("onboarding", List.of("greet", "verify", "activate"));

        assertEquals(3, process.stepCount());
    }
}
