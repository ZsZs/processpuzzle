package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort.DirectoryMatch;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What base-calendar may learn of a person: a subject and a name, never an address, a role, or a disabled account. */
class CalendarDirectoryAdapterTest {

    private static DirectoryUser user(String id, String first, String last, String username, boolean enabled) {
        return new DirectoryUser(id, username, "private@example.test", first, last, enabled, true, Instant.EPOCH, List.of("staff"));
    }

    @Test
    void searchReturnsSubjectsAndNamesOfTheEnabledPeopleOnly() {
        UserDirectoryPort directory = mock(UserDirectoryPort.class);
        when(directory.findUsers(anyString(), anyString(), anyInt(), anyInt())).thenReturn(new DirectoryPage(
                List.of(user("sub-bob", "Bob", "Builder", "bob", true), user("sub-off", "Off", "Boarded", "off", false)), 2, 0, 20));

        List<DirectoryMatch> matches = new CalendarDirectoryAdapter(directory).search("acme", "bo");

        assertThat(matches).containsExactly(new DirectoryMatch("sub-bob", "Bob Builder"));
    }

    @Test
    void aPersonWithoutANameIsShownByTheirUsername() {
        UserDirectoryPort directory = mock(UserDirectoryPort.class);
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.of(user("sub-x", null, " ", "xavier", true)));

        assertThat(new CalendarDirectoryAdapter(directory).find("acme", "sub-x")).contains(new DirectoryMatch("sub-x", "xavier"));
    }

    @Test
    void aDisabledOrUnknownPersonCannotBeFound() {
        UserDirectoryPort directory = mock(UserDirectoryPort.class);
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.empty());
        assertThat(new CalendarDirectoryAdapter(directory).find("acme", "sub-none")).isEmpty();

        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.of(user("sub-off", "O", "F", "off", false)));
        assertThat(new CalendarDirectoryAdapter(directory).find("acme", "sub-off")).isEmpty();
    }

    @Test
    void aSearchFindsAPersonByAnyPartOfTheirNameAndCannotBeWidenedByAnAsterisk() {
        assertThat(CalendarDirectoryAdapter.infix("ee0f4a8e")).isEqualTo("*ee0f4a8e*");
        assertThat(CalendarDirectoryAdapter.infix(" b*ob ")).isEqualTo("*bob*");
    }

    @Test
    void theOrganizationKeyIsTheRealm() {
        UserDirectoryPort directory = mock(UserDirectoryPort.class);
        when(directory.findUser(any(), any())).thenReturn(Optional.empty());

        new CalendarDirectoryAdapter(directory).find("acme", "sub-x");

        org.mockito.Mockito.verify(directory).findUser("acme", "sub-x");
    }
}
