package com.processpuzzle.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration — in particular that its only
 * permitted reach into other feature modules is {@code basestate :: domain} and
 * {@code rule :: usecase} / {@code rule :: domain}, and that {@code definition} and
 * {@code execution} only cross into each other through the ports declared in
 * {@code definition.usecases.outbound} / {@code execution.usecases.outbound}, never by reaching
 * into each other's repositories directly.
 */
class WorkflowModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
