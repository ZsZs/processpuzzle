package com.processpuzzle;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The authoritative modularity check: this is the only build in which every library is on the
 * classpath at once, so it is the only place where the complete module graph — and every declared
 * dependency in it — is verified.
 *
 * <p>Bootstrapped from the application class rather than a package name so that Modulith picks up the
 * {@code @Modulith} metadata (system name, and any shared modules added later).
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ProcessPuzzleBackendApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }

    /**
     * Prints the detected modules with their exposed named interfaces. Not an assertion — the point is
     * that a reviewer can see the structure the annotations actually produced in the build log,
     * instead of inferring it from a green tick.
     */
    @Test
    void writesModuleStructureToTheLog() {
        modules.forEach(module -> System.out.println(module.toString(modules)));
    }
}
