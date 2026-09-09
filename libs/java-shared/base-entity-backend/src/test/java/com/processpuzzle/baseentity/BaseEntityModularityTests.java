package com.processpuzzle.baseentity;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class BaseEntityModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
