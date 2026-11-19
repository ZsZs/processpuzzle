package com.processpuzzle.calendar.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** The mapping binds, the lists survive as JSON, and the derived finders are scoped by organization and channel. */
@DataJpaTest(showSql = false)
// PostgreSQL mode, as the other JPA tests here: the role and state lists are jsonb.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-definition-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.calendar.definition.domain")
@EnableJpaRepositories("com.processpuzzle.calendar.definition.domain")
class CalendarDefinitionPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private CalendarDefinitionRepository repository;

    @Test
    void roundTripsEveryFieldIncludingTheListsAndGetsVersionAndTimestamps() {
        CalendarDefinition definition = CalendarDefinitionTest.definition("meeting-room-a");
        definition.setDescription("The big room");
        definition.setKind(CalendarKind.RESOURCE);
        definition.setVisibility(CalendarVisibility.ROLES);
        definition.setReadRoles(new ArrayList<>(List.of("staff", "guest")));
        definition.setBookRoles(new ArrayList<>(List.of("staff")));
        definition.setManageRoles(new ArrayList<>(List.of("office-admin")));
        definition.setPublicDetail(PublicDetail.FULL);
        definition.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED, EntryKind.RECURRING)));
        definition.setCapacity(3);
        definition.setSlotMinutes(30);
        definition.setMinDurationMinutes(30);
        definition.setMaxDurationMinutes(480);
        definition.setHorizonDays(180);
        repository.saveAndFlush(definition);

        CalendarDefinition reloaded = repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "meeting-room-a", Channel.DRAFT).orElseThrow();

        assertThat(reloaded.getDescription()).isEqualTo("The big room");
        assertThat(reloaded.getKind()).isEqualTo(CalendarKind.RESOURCE);
        assertThat(reloaded.getVisibility()).isEqualTo(CalendarVisibility.ROLES);
        assertThat(reloaded.getReadRoles()).containsExactly("staff", "guest");
        assertThat(reloaded.getBookRoles()).containsExactly("staff");
        assertThat(reloaded.getManageRoles()).containsExactly("office-admin");
        assertThat(reloaded.getPublicDetail()).isEqualTo(PublicDetail.FULL);
        assertThat(reloaded.getAllowedKinds()).containsExactly(EntryKind.TIMED, EntryKind.RECURRING);
        assertThat(reloaded.getCapacity()).isEqualTo(3);
        assertThat(reloaded.getSlotMinutes()).isEqualTo(30);
        assertThat(reloaded.getMinDurationMinutes()).isEqualTo(30);
        assertThat(reloaded.getMaxDurationMinutes()).isEqualTo(480);
        assertThat(reloaded.getHorizonDays()).isEqualTo(180);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void aDraftAndItsReleasedCopyAreTwoRowsOfOneKey() {
        CalendarDefinition draft = repository.saveAndFlush(CalendarDefinitionTest.definition("team-calendar"));
        repository.saveAndFlush(CalendarDefinition.releasedCopyOf(draft));

        assertThat(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.DRAFT)).isPresent();
        assertThat(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "team-calendar", Channel.RELEASED)).isPresent();
        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.RELEASED)).hasSize(1);
    }

    @Test
    void findersAreScopedByOrganizationAndOrderedByKey() {
        repository.saveAndFlush(CalendarDefinitionTest.definition("b-calendar"));
        repository.saveAndFlush(CalendarDefinitionTest.definition("a-calendar"));
        CalendarDefinition elsewhere = CalendarDefinitionTest.definition("a-calendar");
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.DRAFT))
                .extracting(CalendarDefinition::getDefinitionKey).containsExactly("a-calendar", "b-calendar");
        assertThat(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-2", "b-calendar", Channel.DRAFT)).isEmpty();
    }

    @Test
    void erasesOneTenantsDefinitionsInOneStatement() {
        repository.saveAndFlush(CalendarDefinitionTest.definition("a-calendar"));
        CalendarDefinition elsewhere = CalendarDefinitionTest.definition("a-calendar");
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        assertThat(repository.eraseByOrgKey("org-1")).isEqualTo(1);

        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.DRAFT)).isEmpty();
        assertThat(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-2", Channel.DRAFT)).hasSize(1);
    }
}
