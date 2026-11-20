package com.processpuzzle.calendar.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionTest;
import com.processpuzzle.calendar.definition.usecases.exception.CalendarDefinitionNotFoundException;
import com.processpuzzle.calendar.definition.usecases.inbound.FindCalendarDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindCalendarDefinitionTest {

    private CalendarDefinitionRepository repository;
    private OrganizationGuard guard;
    private FindCalendarDefinition find;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarDefinitionRepository.class);
        guard = mock(OrganizationGuard.class);
        find = new FindCalendarDefinition(repository, guard);
    }

    @Test
    void readingADraftNeedsTheDesignRightAndReadingTheReleasedRowOnlyMembership() {
        CalendarDefinition draft = CalendarDefinitionTest.definition("team-calendar");
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.DRAFT)).thenReturn(Optional.of(draft));
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.RELEASED)).thenReturn(Optional.of(draft));

        assertThat(find.find("org-1", "team-calendar", Channel.DRAFT)).isSameAs(draft);
        verify(guard).requireDesign("org-1");

        assertThat(find.find("org-1", "team-calendar", Channel.RELEASED)).isSameAs(draft);
        verify(guard).requireAccess("org-1");
    }

    @Test
    void anUnknownKeyIsNotFound() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "nope", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> find.find("org-1", "nope", Channel.DRAFT)).isInstanceOf(CalendarDefinitionNotFoundException.class);
    }

    @Test
    void listsAnOrganizationsDefinitionsOfOneChannel() {
        CalendarDefinition draft = CalendarDefinitionTest.definition("team-calendar");
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.RELEASED)).thenReturn(List.of(draft));

        assertThat(find.findAll("org-1", Channel.RELEASED)).containsExactly(draft);
    }

    @Test
    void refusesANonMemberOutright() {
        doThrow(new OrganizationAccessDeniedException("org-2")).when(guard).requireAccess("org-2");

        assertThatThrownBy(() -> find.findAll("org-2", Channel.RELEASED)).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void theReleasedLookupForInternalCallersToleratesAMissingKeyAndRow() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "gone", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(find.released("org-1", null)).isEmpty();
        assertThat(find.released("org-1", "gone")).isEmpty();
    }
}
