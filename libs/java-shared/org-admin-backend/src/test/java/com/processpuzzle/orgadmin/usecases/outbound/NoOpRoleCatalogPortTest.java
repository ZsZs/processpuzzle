package com.processpuzzle.orgadmin.usecases.outbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpRoleCatalogPortTest {

    @Test
    void acceptsRolesSilentlyBecauseThereIsNothingToCreateThemIn() {
        assertThatCode(() -> new NoOpRoleCatalogPort().ensureRoles("acme", List.of("editor"), "d"))
                .doesNotThrowAnyException();
    }

    /** Reporting a role as created when it exists nowhere is the one lie here somebody would act on. */
    @Test
    void refusesAnAdministratorsChangeBecauseThereIsNothingToChange() {
        NoOpRoleCatalogPort port = new NoOpRoleCatalogPort();

        assertThatThrownBy(() -> port.createRole("acme", "x", null)).isInstanceOf(DirectoryUnavailableException.class);
        assertThatThrownBy(() -> port.updateDescription("acme", "x", null)).isInstanceOf(DirectoryUnavailableException.class);
        assertThatThrownBy(() -> port.deleteRole("acme", "x")).isInstanceOf(DirectoryUnavailableException.class);
        assertThatThrownBy(() -> port.hasHolders("acme", "x")).isInstanceOf(DirectoryUnavailableException.class);
    }
}
