package com.processpuzzle.platformadmin;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration, which states that the feature
 * reaches into no other feature module — only {@code core} and {@code shared}.
 *
 * <p>The declaration matters more here than in a leaf feature, because this module is what base-app
 * and org-admin now depend on. An edge added <em>out</em> of {@code platformadmin} into either of
 * them would be a cycle, and the failure would surface as this test rather than as a mysterious
 * bean-ordering problem later.
 *
 * <p>The base package is the Modulith root rather than this library's own package, matching every
 * sibling library: verifying from {@code com.processpuzzle} is what makes the named interfaces of
 * the other modules on the classpath visible to the check.
 */
class PlatformAdminModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
