package com.processpuzzle.rule;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration against whatever else happens to be
 * on its classpath, so a violation fails here rather than only in the application build.
 *
 * <p>The base package is {@code com.processpuzzle} — the Modulith root — not this library's own
 * package: modules are the root's direct sub-packages, so anything narrower would find no modules at
 * all. Modules present only as generated api-contracts packages ({@code app.model} and the like)
 * carry no declaration and are therefore unrestricted, so the partial classpath cannot produce
 * spurious violations.
 */
class RuleModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
