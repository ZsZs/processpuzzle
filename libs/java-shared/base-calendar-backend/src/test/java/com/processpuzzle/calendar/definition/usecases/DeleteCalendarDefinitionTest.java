package com.processpuzzle.calendar.definition.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionTest;
import com.processpuzzle.calendar.definition.usecases.exception.CalendarDefinitionNotFoundException;
import com.processpuzzle.calendar.definition.usecases.inbound.DeleteCalendarDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeleteCalendarDefinitionTest {

    private static final String ORG = "org-1";

    private CalendarDefinitionRepository repository;
    private OrganizationGuard guard;
    private DeleteCalendarDefinition delete;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarDefinitionRepository.class);
        guard = mock(OrganizationGuard.class);
        delete = new DeleteCalendarDefinition(repository, guard);
    }

    @Test
    void deletesTheDraftAndNeedsTheDesignRight() {
        CalendarDefinition draft = CalendarDefinitionTest.definition("team-calendar");
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(ORG, "team-calendar", Channel.DRAFT)).thenReturn(Optional.of(draft));

        delete.delete(ORG, "team-calendar");

        verify(guard).requireDesign(ORG);
        verify(repository).delete(draft);
    }

    @Test
    void reportsAMissingDraft() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(ORG, "gone", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delete.delete(ORG, "gone")).isInstanceOf(CalendarDefinitionNotFoundException.class);
    }

    @Test
    void refusesACallerWithoutTheDesignRightBeforeLookingAnything() {
        doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireDesign(ORG);

        assertThatThrownBy(() -> delete.delete(ORG, "team-calendar")).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(repository, never()).delete(any());
    }
}
