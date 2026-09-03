package com.processpuzzle.orgadmin;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies this library's {@code @ApplicationModule} declaration — in particular that it reaches into
 * {@code platform-admin} only through the four named interfaces it declares, and that no edge ever
 * runs back the other way. A cycle between the two would be the natural mistake here, since both are
 * "admin", and it would surface as this test rather than as a bean-ordering puzzle later.
 *
 * <p>The base package is the Modulith root rather than this library's own, matching every sibling:
 * verifying from {@code com.processpuzzle} is what makes the other modules' named interfaces visible
 * to the check.
 */
class OrgAdminModularityTests {

    private final ApplicationModules modules = ApplicationModules.of("com.processpuzzle");

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
