package com.processpuzzle.calendar.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionTest;
import com.processpuzzle.calendar.definition.usecases.exception.InvalidCalendarDefinitionException;
import com.processpuzzle.calendar.definition.usecases.exception.StaleCalendarDefinitionException;
import com.processpuzzle.calendar.definition.usecases.inbound.SaveCalendarDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaveCalendarDefinitionTest {

    private CalendarDefinitionRepository repository;
    private OrganizationGuard guard;
    private SaveCalendarDefinition save;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarDefinitionRepository.class);
        guard = mock(OrganizationGuard.class);
        when(repository.save(any(CalendarDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        save = new SaveCalendarDefinition(repository, guard);
    }

    private static CalendarDefinition desired() {
        CalendarDefinition desired = CalendarDefinitionTest.definition("ignored");
        desired.setOrgKey("another-org");
        desired.setReadRoles(new ArrayList<>(List.of("sailor")));
        desired.setBookRoles(new ArrayList<>(List.of("dispatcher")));
        return desired;
    }

    @Test
    void createsTheDraftUnderTheCallersOrganizationAndTheKeyOfThePath() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.DRAFT)).thenReturn(Optional.empty());
        CalendarDefinition input = desired();
        input.setVersion(9L);

        CalendarDefinition saved = save.save("org-1", "team-calendar", input);

        assertThat(saved.getOrgKey()).isEqualTo("org-1");
        assertThat(saved.getDefinitionKey()).isEqualTo("team-calendar");
        assertThat(saved.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(saved.getVersion()).isNull();
        inOrder(guard, repository).verify(guard).requireDesign("org-1");
    }

    @Test
    void announcesTheRolesItNamesToTheRoleCatalog() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        save.save("org-1", "team-calendar", desired());

        verify(guard).declareRoles("org-1", "calendar definition 'team-calendar'", Set.of("sailor", "dispatcher"));
    }

    @Test
    void replacesTheExistingDraftKeepingItsIdentity() {
        CalendarDefinition existing = CalendarDefinitionTest.definition("team-calendar");
        existing.setVersion(3L);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.DRAFT)).thenReturn(Optional.of(existing));
        CalendarDefinition input = desired();
        input.setName("Renamed");
        input.setVersion(3L);

        CalendarDefinition saved = save.save("org-1", "team-calendar", input);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getName()).isEqualTo("Renamed");
        assertThat(saved.getReadRoles()).containsExactly("sailor");
    }

    @Test
    void refusesAWriteBasedOnAStaleVersionButOverwritesWhenNoneIsGiven() {
        CalendarDefinition existing = CalendarDefinitionTest.definition("team-calendar");
        existing.setVersion(4L);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.DRAFT)).thenReturn(Optional.of(existing));
        CalendarDefinition stale = desired();
        stale.setVersion(3L);

        assertThatThrownBy(() -> save.save("org-1", "team-calendar", stale)).isInstanceOf(StaleCalendarDefinitionException.class);
        verify(repository, never()).save(any());

        assertThat(save.save("org-1", "team-calendar", desired()).getName()).isEqualTo("Team calendar");
    }

    @Test
    void refusesAnInvalidDefinitionAndStoresNothing() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());
        CalendarDefinition invalid = desired();
        invalid.setName(" ");

        assertThatThrownBy(() -> save.save("org-1", "team-calendar", invalid))
                .isInstanceOfSatisfying(InvalidCalendarDefinitionException.class,
                        e -> assertThat(e.getViolations()).singleElement().asString().contains("'name'"));
        verify(repository, never()).save(any());
        verify(guard, never()).declareRoles(any(), any(), any());
    }

    @Test
    void refusesAMalformedKeyFromThePath() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        CalendarDefinition input = desired();

        assertThatThrownBy(() -> save.save("org-1", "Order Discussion", input))
                .isInstanceOf(InvalidCalendarDefinitionException.class).hasMessageContaining("'key'");
    }

    @Test
    void needsTheDesignRight() {
        doThrow(new OrganizationAccessDeniedException("org-1")).when(guard).requireDesign("org-1");

        assertThatThrownBy(() -> save.save("org-1", "team-calendar", desired()))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        verify(repository, never()).save(any());
    }
}
