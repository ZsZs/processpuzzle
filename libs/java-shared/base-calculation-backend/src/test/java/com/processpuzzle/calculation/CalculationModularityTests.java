package com.processpuzzle.calculation;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration: base-calculation depends on {@code core} and
 * {@code shared} only. An import of base-entity or any other feature fails here.
 */
class CalculationModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
