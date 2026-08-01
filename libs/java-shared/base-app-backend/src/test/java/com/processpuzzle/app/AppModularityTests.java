package com.processpuzzle.app;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration — in particular that it reaches into
 * Base Rule only through the {@code rule :: usecase} and {@code rule :: domain} named interfaces, and
 * not into the rule repository or the rule engine.
 *
 * <p>See {@code RuleModularityTests} for why the base package is the Modulith root rather than this
 * library's own package.
 */
class AppModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
