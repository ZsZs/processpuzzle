package com.processpuzzle.event;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration: base-event depends on {@code core}
 * and {@code shared} only. An import of base-entity, base-state or base-workflow fails here.
 */
class EventModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
