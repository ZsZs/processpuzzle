package com.processpuzzle.orgadmin.usecases.outbound;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpRoleProvisioningPortTest {

    @Test
    void acceptsRolesSilentlyBecauseThereIsNothingToCreateThemIn() {
        assertThatCode(() -> new NoOpRoleProvisioningPort().ensureRoles("acme", List.of("editor"), "d"))
                .doesNotThrowAnyException();
    }
}
