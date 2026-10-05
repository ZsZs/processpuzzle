package com.processpuzzle.starter;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration, which states that the feature
 * depends on no other module. The first undeclared edge — say, an import of a feature library's
 * use case instead of going through the participant SPI — fails here.
 */
class StarterModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
