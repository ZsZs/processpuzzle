package com.processpuzzle.orgadmin.usecases.outbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The asymmetry is the whole design of this class, so it is what gets tested: reads answer empty,
 * writes refuse.
 *
 * <p>A no-op that silently accepted an invitation would report success for a user that does not
 * exist and never will — the administrator would find out when the invitee could not log in, with
 * nothing anywhere to explain why. Refusing says the truth.
 */
class NoOpUserDirectoryPortTest {

    private static final UserDirectoryPort.NewUser USER =
            new UserDirectoryPort.NewUser("ada", "ada@my-org.example", null, null);

    private final UserDirectoryPort directory = new NoOpUserDirectoryPort();

    @Test
    void readsAnswerEmptyBecauseAnEmptyDirectoryIsTheTruth() {
        assertThat(directory.findUsers("my-org", null, 0, 20).content()).isEmpty();
        assertThat(directory.findUsers("my-org", null, 0, 20).totalElements()).isZero();
        assertThat(directory.findUser("my-org", "any")).isEmpty();
        assertThat(directory.findRoles("my-org")).isEmpty();
        assertThat(directory.findUserRoles("my-org", "any")).isEmpty();
    }

    /** The paging metadata is echoed back, so a client's pager does not divide by zero. */
    @Test
    void anEmptyPageStillReportsThePagingItWasAskedFor() {
        DirectoryPage page = directory.findUsers("my-org", null, 3, 10);

        assertThat(page.number()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    void everyWriteIsRefusedWithAnExplanation() {
        assertThatThrownBy(() -> directory.inviteUser("my-org", USER, List.of()))
                .isInstanceOf(DirectoryUnavailableException.class)
                .hasMessageContaining("keycloak.admin.client-secret");
        assertThatThrownBy(() -> directory.updateUser("my-org", "any",
                new UserDirectoryPort.UserProfile(null, null, null, false)))
                .isInstanceOf(DirectoryUnavailableException.class);
        assertThatThrownBy(() -> directory.deleteUser("my-org", "any"))
                .isInstanceOf(DirectoryUnavailableException.class);
        assertThatThrownBy(() -> directory.replaceRoles("my-org", "any", List.of()))
                .isInstanceOf(DirectoryUnavailableException.class);
    }
}
