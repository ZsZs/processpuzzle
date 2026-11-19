package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.processpuzzle.starter.domain.DataSpaceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSpaceDirectoryAdapterTest {

    @Mock
    private DataSpaceRepository dataSpaces;
    @InjectMocks
    private DataSpaceDirectoryAdapter directory;

    @Test
    void member_isInTheCommonOfItsStarter() {
        when(dataSpaces.findCommonKeysOf("club")).thenReturn(List.of("starter-common"));

        assertThat(directory.commonsOf("club")).containsExactly("starter-common");
    }

    /** An organization may install several starters, so it is a member of the common of each, in key order. */
    @Test
    void member_isInTheCommonOfEveryStarterItInstalled() {
        when(dataSpaces.findCommonKeysOf("club")).thenReturn(List.of("billing-common", "starter-common"));

        assertThat(directory.commonsOf("club")).containsExactly("billing-common", "starter-common");
    }

    @Test
    void organizationWithoutAStarterWithACommon_hasNone() {
        when(dataSpaces.findCommonKeysOf("club")).thenReturn(List.of());

        assertThat(directory.commonsOf("club")).isEmpty();
    }

    @Test
    void theCommonItself_isNotItsOwnMember() {
        when(dataSpaces.findCommonKeysOf("starter-common")).thenReturn(List.of("starter-common"));

        assertThat(directory.commonsOf("starter-common")).isEmpty();
    }

    @Test
    void aCommonThatInstalledAnotherStartersCommonIsStillNotItsOwnMember() {
        when(dataSpaces.findCommonKeysOf("starter-common")).thenReturn(List.of("billing-common", "starter-common"));

        assertThat(directory.commonsOf("starter-common")).containsExactly("billing-common");
    }

    @Test
    void isCommon_asksTheRegistry() {
        when(dataSpaces.existsById("starter-common")).thenReturn(true);

        assertThat(directory.isCommon("starter-common")).isTrue();
        assertThat(directory.isCommon("club")).isFalse();
    }

    @Test
    void membersOf_listsTheOrganizationsOfTheCommon() {
        when(dataSpaces.findMemberKeysOf("starter-common")).thenReturn(List.of("club-a", "club-b"));

        assertThat(directory.membersOf("starter-common")).containsExactly("club-a", "club-b");
    }
}
