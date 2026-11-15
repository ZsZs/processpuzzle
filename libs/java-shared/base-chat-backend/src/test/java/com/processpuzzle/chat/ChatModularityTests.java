package com.processpuzzle.chat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration: base-chat depends on {@code core}
 * and {@code shared} only. An import of base-entity, base-workflow or any other feature fails here.
 */
class ChatModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
