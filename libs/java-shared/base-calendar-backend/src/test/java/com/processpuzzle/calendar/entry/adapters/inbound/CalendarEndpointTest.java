package com.processpuzzle.calendar.entry.adapters.inbound;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.allDayEntry;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.timedEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.calendar.common.CalendarApiExceptionHandler;
import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.calendar.entry.usecases.inbound.GetCalendar;
import com.processpuzzle.calendar.entry.usecases.inbound.ListOccurrences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of a calendar and of the occurrence window, which anonymous callers use. */
class CalendarEndpointTest {

    private static final String BASE = "/organizations/org-1/calendar";

    private GetCalendar getCalendar;
    private ListOccurrences listOccurrences;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        getCalendar = mock(GetCalendar.class);
        listOccurrences = mock(ListOccurrences.class);
        mvc = MockMvcBuilders.standaloneSetup(new CalendarEndpoint(getCalendar, listOccurrences, new EntryMapper()))
                .setControllerAdvice(new CalendarApiExceptionHandler())
                .build();
    }

    @Test
    void aCalendarReportsItsRulesTheZoneAndTheCallersPermissionsButNotItsRoleLists() throws Exception {
        CalendarDefinition definition = calendar(CalendarVisibility.ROLES, PublicDetail.FULL);
        definition.setReadRoles(new java.util.ArrayList<>(List.of("secret-read-role")));
        definition.setSlotMinutes(30);
        definition.setHorizonDays(180);
        when(getCalendar.get(ORG, CALENDAR)).thenReturn(
                new GetCalendar.Summary(definition, new CalendarAccess(true, true, false, true), ZoneId.of("Europe/Budapest")));

        String body = mvc.perform(get(BASE + "/calendars/" + CALENDAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(CALENDAR))
                .andExpect(jsonPath("$.name").value("Team calendar"))
                .andExpect(jsonPath("$.kind").value("TEAM"))
                .andExpect(jsonPath("$.effectiveTimeZone").value("Europe/Budapest"))
                .andExpect(jsonPath("$.allowedKinds[0]").value("TIMED"))
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.horizonDays").value(180))
                .andExpect(jsonPath("$.permissions.canBook").value(true))
                .andExpect(jsonPath("$.permissions.canManage").value(false))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("secret-read-role").doesNotContain("readRoles");
    }

    @Test
    void aCalendarTheCallerMayNotReadIs404() throws Exception {
        when(getCalendar.get(ORG, "hidden")).thenThrow(new CalendarNotFoundException());

        mvc.perform(get(BASE + "/calendars/hidden")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorId").value("calendar.not-found"));
    }

    @Test
    void listsOccurrencesAsTimedAndAllDayWithBusyOnesStripped() throws Exception {
        CalendarEntry timed = timedEntry(ALICE, EntryVisibility.PUBLIC);
        CalendarEntry holiday = allDayEntry(ALICE, LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-27"));
        when(listOccurrences.list(any(), any(), any(), any())).thenReturn(List.of(
                new EntryView(timed, false, true, true),
                new EntryView(holiday, true, false, false)));

        mvc.perform(get(BASE + "/occurrences").param("calendars", CALENDAR + ",other")
                        .param("from", "2026-12-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].allDay").value(false))
                .andExpect(jsonPath("$[0].busy").value(false))
                .andExpect(jsonPath("$[0].title").value("Planning"))
                .andExpect(jsonPath("$[0].start").exists())
                .andExpect(jsonPath("$[0].mine").value(true))
                .andExpect(jsonPath("$[1].allDay").value(true))
                .andExpect(jsonPath("$[1].busy").value(true))
                .andExpect(jsonPath("$[1].title").value(nullValue()))
                .andExpect(jsonPath("$[1].startDate").value("2026-12-24"))
                .andExpect(jsonPath("$[1].endDate").value("2026-12-27"))
                .andExpect(jsonPath("$[1].start").value(nullValue()));

        verify(listOccurrences).list(ORG, List.of(CALENDAR, "other"), Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"));
    }

    @Test
    void aBadWindowIs400AndMissingParametersAreToo() throws Exception {
        when(listOccurrences.list(any(), any(), any(), any())).thenThrow(new InvalidEntryRequestException("'to' must be after 'from'"));

        mvc.perform(get(BASE + "/occurrences").param("calendars", CALENDAR)
                        .param("from", "2026-12-31T00:00:00Z").param("to", "2026-12-01T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorId").value("calendar.invalid-request"));
        mvc.perform(get(BASE + "/occurrences").param("calendars", CALENDAR)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/occurrences").param("from", "2026-12-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
