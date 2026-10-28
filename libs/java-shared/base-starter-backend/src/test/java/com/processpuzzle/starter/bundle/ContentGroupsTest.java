package com.processpuzzle.starter.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.DefinitionKinds;
import org.junit.jupiter.api.Test;

class ContentGroupsTest {

    @Test
    void modulesAndAppsAreGroupsOfTheirOwnKinds() {
        assertThat(ContentGroups.kindOf("modules")).contains(DefinitionKinds.MODULE);
        assertThat(ContentGroups.kindOf("apps")).contains(DefinitionKinds.APP);
    }

    @Test
    void anUnknownGroupHasNoKind() {
        assertThat(ContentGroups.kindOf("themes")).isEmpty();
    }
}
