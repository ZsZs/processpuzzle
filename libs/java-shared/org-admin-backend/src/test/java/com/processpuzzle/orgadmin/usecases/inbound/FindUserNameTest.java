package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindUserNameTest {

    private final UserDirectoryPort directory = mock(UserDirectoryPort.class);

    @Test
    void namesTheUserByFullNameThenEmailThenUsername() {
        when(directory.findUser("acme", "u-1")).thenReturn(Optional.of(user("Ada", "Lovelace", "ada@acme.test")));
        when(directory.findUser("acme", "u-2")).thenReturn(Optional.of(user(null, " ", "grace@acme.test")));
        when(directory.findUser("acme", "u-3")).thenReturn(Optional.of(user(null, null, null)));

        assertThat(useCase().displayNameOf("acme", "u-1")).contains("Ada Lovelace");
        assertThat(useCase().displayNameOf("acme", "u-2")).contains("grace@acme.test");
        assertThat(useCase().displayNameOf("acme", "u-3")).contains("username");
    }

    @Test
    void anUnknownUserHasNoName() {
        when(directory.findUser("acme", "nobody")).thenReturn(Optional.empty());

        assertThat(useCase().displayNameOf("acme", "nobody")).isEmpty();
    }

    // Best effort by contract: the caller is assigning a task, and a directory outage must not fail that.
    @Test
    void anUnreachableDirectoryAnswersNothingRatherThanFailing() {
        when(directory.findUser("acme", "u-1")).thenThrow(new DirectoryUnavailableException("down"));

        assertThat(useCase().displayNameOf("acme", "u-1")).isEmpty();
    }

    private FindUserName useCase() {
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantRealmDirectory> tenants = mock(ObjectProvider.class);
        when(tenants.getIfUnique(any())).thenReturn(TenantRealmDirectory.BY_CONVENTION);
        return new FindUserName(tenants, directory);
    }

    private static DirectoryUser user(String firstName, String lastName, String email) {
        return new DirectoryUser("id", "username", email, firstName, lastName, true, true, Instant.now(), List.of());
    }
}
