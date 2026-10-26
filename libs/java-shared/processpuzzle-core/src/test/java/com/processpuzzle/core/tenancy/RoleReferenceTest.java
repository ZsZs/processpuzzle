package com.processpuzzle.core.tenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleReferenceTest {

    /** What makes a role {@code workflow}-managed rather than the administrator's. */
    @Test
    void aRoleDefinitionIsTheOnlyKindThatIsOne() {
        assertThat(new RoleReference("workflow", RoleReference.ROLE_DEFINITION, "reviewer", "Reviewer").isRoleDefinition())
                .isTrue();
        assertThat(new RoleReference("document", "reader-role", "doc-1", "Handbook").isRoleDefinition()).isFalse();
        assertThat(new RoleReference("document", null, "doc-1", null).isRoleDefinition()).isFalse();
    }
}
