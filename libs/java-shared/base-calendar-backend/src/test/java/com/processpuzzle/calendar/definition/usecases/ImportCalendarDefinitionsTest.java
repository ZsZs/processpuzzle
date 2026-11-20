package com.processpuzzle.calendar.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.domain.CalendarKind;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.definition.usecases.inbound.ImportCalendarDefinitions;
import com.processpuzzle.calendar.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.core.definition.Channel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportCalendarDefinitionsTest {

    private static final String TWO = """
            calendar-definitions:
              - key: team-calendar
                name: Team calendar
              - key: meeting-room-a
                name: Meeting room A
                kind: RESOURCE
                visibility: ROLES
                readRoles: [staff]
                bookRoles: [staff]
                manageRoles: [office-admin]
                publicDetail: FULL
                allowedKinds: [TIMED]
                capacity: 2
                slotMinutes: 30
                minDurationMinutes: 30
                maxDurationMinutes: 480
                horizonDays: 180
            """;

    private CalendarDefinitionRepository repository;
    private ImportCalendarDefinitions importer;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarDefinitionRepository.class);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());
        importer = new ImportCalendarDefinitions(repository);
    }

    private ImportOutcome run(String yaml, boolean release) throws IOException {
        return importer.execute("org-1", new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), release);
    }

    @Test
    void createsTheDraftsWithTheCautiousDefaultsAndTheListedFields() throws IOException {
        ImportOutcome outcome = run(TWO, false);

        assertThat(outcome.created()).isEqualTo(2);
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.written()).containsExactly("team-calendar", "meeting-room-a");
        ArgumentCaptor<CalendarDefinition> saved = ArgumentCaptor.forClass(CalendarDefinition.class);
        verify(repository, times(2)).save(saved.capture());

        CalendarDefinition team = saved.getAllValues().get(0);
        assertThat(team.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(team.getKind()).isEqualTo(CalendarKind.TEAM);
        assertThat(team.getVisibility()).isEqualTo(CalendarVisibility.AUTHENTICATED);
        assertThat(team.getPublicDetail()).isEqualTo(PublicDetail.BUSY_ONLY);

        CalendarDefinition room = saved.getAllValues().get(1);
        assertThat(room.getKind()).isEqualTo(CalendarKind.RESOURCE);
        assertThat(room.getVisibility()).isEqualTo(CalendarVisibility.ROLES);
        assertThat(room.getReadRoles()).containsExactly("staff");
        assertThat(room.getBookRoles()).containsExactly("staff");
        assertThat(room.getManageRoles()).containsExactly("office-admin");
        assertThat(room.getPublicDetail()).isEqualTo(PublicDetail.FULL);
        assertThat(room.getAllowedKinds()).containsExactly(EntryKind.TIMED);
        assertThat(room.getCapacity()).isEqualTo(2);
        assertThat(room.getSlotMinutes()).isEqualTo(30);
        assertThat(room.getMinDurationMinutes()).isEqualTo(30);
        assertThat(room.getMaxDurationMinutes()).isEqualTo(480);
        assertThat(room.getHorizonDays()).isEqualTo(180);
    }

    @Test
    void alsoWritesTheReleasedRowsWhenAskedTo() throws IOException {
        run(TWO, true);

        ArgumentCaptor<CalendarDefinition> saved = ArgumentCaptor.forClass(CalendarDefinition.class);
        verify(repository, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CalendarDefinition::getChannel)
                .containsExactly(Channel.DRAFT, Channel.RELEASED, Channel.DRAFT, Channel.RELEASED);
    }

    @Test
    void replacesAnExistingRowInsteadOfRejectingIt() throws IOException {
        CalendarDefinition existing = CalendarDefinition.builder().orgKey("org-1").definitionKey("team-calendar").name("Old").build();
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.DRAFT)).thenReturn(Optional.of(existing));

        ImportOutcome outcome = run(TWO, false);

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(outcome.updatedKeys()).containsExactly("team-calendar");
        assertThat(existing.getName()).isEqualTo("Team calendar");
    }

    @Test
    void isAllOrNothingAndReportsEveryProblem() throws IOException {
        ImportOutcome outcome = run("""
                calendar-definitions:
                  - key: ok-one
                    name: Fine
                  - key: Bad Key
                    name: Broken
                  - key: ok-one
                    name: Twice
                  - key: public-without-name
                  - key: roles-without-roles
                    name: Roles
                    visibility: ROLES
                """, true);

        assertThat(outcome.errors()).hasSize(4)
                .anyMatch(error -> error.contains("'Bad Key'"))
                .anyMatch(error -> error.contains("Duplicate key"))
                .anyMatch(error -> error.contains("'public-without-name'") && error.contains("'name'"))
                .anyMatch(error -> error.contains("'roles-without-roles'") && error.contains("read role"));
        assertThat(outcome.written()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void anEmptyFileOrOneWithoutTheListImportsNothing() throws IOException {
        assertThat(run("", true).written()).isEmpty();
        assertThat(run("other: 1\n", true).written()).isEmpty();
        assertThat(run("calendar-definitions: []\n", true)).isEqualTo(new ImportOutcome(0, 0, List.of(), List.of()));
        verify(repository, never()).save(any());
    }

    @Test
    void removesTheDraftsOfAnOrganization() {
        CalendarDefinition draft = CalendarDefinition.builder().orgKey("org-1").definitionKey("team-calendar").name("Team").build();
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.DRAFT)).thenReturn(List.of(draft));

        assertThat(importer.removeAll("org-1")).containsExactly("team-calendar");
        verify(repository).deleteAll(List.of(draft));
    }
}
