package com.processpuzzle.calendar.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CalendarDefinitionTest {

    /** A valid TEAM calendar named {@code Team calendar} in organization {@code org-1}, with every default. */
    public static CalendarDefinition definition(String key) {
        return CalendarDefinition.builder()
                .orgKey("org-1")
                .definitionKey(key)
                .name("Team calendar")
                .build();
    }

    @Test
    void aMinimalDefinitionIsValidAndTakesTheCautiousDefaults() {
        CalendarDefinition definition = definition("team-calendar");

        assertThat(definition.validate()).isEmpty();
        assertThat(definition.getKind()).isEqualTo(CalendarKind.TEAM);
        assertThat(definition.getVisibility()).isEqualTo(CalendarVisibility.AUTHENTICATED);
        assertThat(definition.getPublicDetail()).isEqualTo(PublicDetail.BUSY_ONLY);
        assertThat(definition.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(definition.getCapacity()).isEqualTo(1);
        assertThat(definition.getReadRoles()).isEmpty();
    }

    @Test
    void acceptsTimedAndAllDayUnlessItNamesKinds() {
        CalendarDefinition definition = definition("team-calendar");

        assertThat(definition.effectiveKinds()).containsExactly(EntryKind.TIMED, EntryKind.ALL_DAY);
        assertThat(definition.accepts(EntryKind.RECURRING)).isFalse();

        definition.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED)));
        assertThat(definition.accepts(EntryKind.TIMED)).isTrue();
        assertThat(definition.accepts(EntryKind.ALL_DAY)).isFalse();
    }

    @Test
    void requiresAWellFormedKeyAndAName() {
        CalendarDefinition definition = CalendarDefinition.builder().definitionKey("Team Calendar").build();

        assertThat(definition.validate()).hasSize(2)
                .anyMatch(error -> error.contains("'key'"))
                .anyMatch(error -> error.contains("'name'"));
    }

    @Test
    void aRolesCalendarNeedsReadRolesAndOthersNeedNone() {
        CalendarDefinition definition = definition("team-calendar");
        definition.setVisibility(CalendarVisibility.ROLES);
        assertThat(definition.validate()).singleElement().asString().contains("read role");

        definition.setReadRoles(new ArrayList<>(List.of("staff")));
        assertThat(definition.validate()).isEmpty();
    }

    @Test
    void refusesBlankRoles() {
        CalendarDefinition definition = definition("team-calendar");
        definition.setBookRoles(new ArrayList<>(List.of("staff", " ")));

        assertThat(definition.validate()).singleElement().asString().contains("blank");
    }

    @Test
    void capacityOnlyBelongsToAResourceAndIsAtLeastOne() {
        CalendarDefinition team = definition("team-calendar");
        team.setCapacity(2);
        assertThat(team.validate()).singleElement().asString().contains("RESOURCE");

        CalendarDefinition room = definition("meeting-room-a");
        room.setKind(CalendarKind.RESOURCE);
        room.setCapacity(2);
        assertThat(room.validate()).isEmpty();
        room.setCapacity(0);
        assertThat(room.validate()).anyMatch(error -> error.contains("'capacity' must be at least 1"));
    }

    @Test
    void checksTheBookingRules() {
        CalendarDefinition definition = definition("team-calendar");
        definition.setSlotMinutes(0);
        definition.setHorizonDays(0);
        assertThat(definition.validate()).hasSize(2)
                .anyMatch(error -> error.contains("'slotMinutes'"))
                .anyMatch(error -> error.contains("'horizonDays'"));

        definition.setSlotMinutes(1441);
        definition.setHorizonDays(30);
        assertThat(definition.validate()).singleElement().asString().contains("'slotMinutes'");

        definition.setSlotMinutes(30);
        definition.setMinDurationMinutes(120);
        definition.setMaxDurationMinutes(60);
        assertThat(definition.validate()).singleElement().asString().contains("must not exceed");

        definition.setMinDurationMinutes(0);
        assertThat(definition.validate()).singleElement().asString().contains("must be positive");
    }

    @Test
    void namesEveryRoleOnceForTheRoleCatalog() {
        CalendarDefinition definition = definition("team-calendar");
        definition.setReadRoles(new ArrayList<>(List.of("staff", "guest")));
        definition.setBookRoles(new ArrayList<>(List.of("staff")));
        definition.setManageRoles(new ArrayList<>(List.of("office-admin")));

        assertThat(definition.allRoles()).containsExactlyInAnyOrder("staff", "guest", "office-admin");
    }

    @Test
    void theFingerprintCoversTheAuthoredContentOnly() {
        CalendarDefinition draft = definition("team-calendar");
        draft.setVersion(7L);
        CalendarDefinition released = CalendarDefinition.releasedCopyOf(draft);

        assertThat(released.getChannel()).isEqualTo(Channel.RELEASED);
        assertThat(released.fingerprint()).isEqualTo(draft.fingerprint());

        draft.setVisibility(CalendarVisibility.PUBLIC);
        assertThat(released.fingerprint()).isNotEqualTo(draft.fingerprint());
    }

    @Test
    void replacingCopiesTheAuthoredFieldsAndLeavesTheIdentity() {
        CalendarDefinition target = definition("team-calendar");
        CalendarDefinition source = definition("other-key");
        source.setName("Renamed");
        source.setVisibility(CalendarVisibility.PUBLIC);
        source.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED, EntryKind.TIMED)));
        source.setBookRoles(new ArrayList<>(List.of("staff")));

        target.replaceWith(source);

        assertThat(target.getDefinitionKey()).isEqualTo("team-calendar");
        assertThat(target.getName()).isEqualTo("Renamed");
        assertThat(target.getVisibility()).isEqualTo(CalendarVisibility.PUBLIC);
        assertThat(target.getAllowedKinds()).containsExactly(EntryKind.TIMED);
        assertThat(target.getBookRoles()).containsExactly("staff").isNotSameAs(source.getBookRoles());
    }
}
