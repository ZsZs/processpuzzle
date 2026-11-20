package com.processpuzzle.calendar.definition.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.calendar.common.CalendarApiExceptionHandler;
import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionTest;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.definition.usecases.exception.CalendarDefinitionNotFoundException;
import com.processpuzzle.calendar.definition.usecases.exception.InvalidCalendarDefinitionException;
import com.processpuzzle.calendar.definition.usecases.exception.StaleCalendarDefinitionException;
import com.processpuzzle.calendar.definition.usecases.inbound.DeleteCalendarDefinition;
import com.processpuzzle.calendar.definition.usecases.inbound.FindCalendarDefinition;
import com.processpuzzle.calendar.definition.usecases.inbound.SaveCalendarDefinition;
import com.processpuzzle.core.definition.Channel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of the definitions: status codes, the shape of the bodies and the translation of input. */
class CalendarDefinitionEndpointTest {

    private static final String BASE = "/organizations/org-1/calendar/definitions";
    private static final String BODY = """
            {"name":"Meeting room A","kind":"RESOURCE","visibility":"ROLES","readRoles":["staff"],
             "bookRoles":["staff"],"manageRoles":["office-admin"],"publicDetail":"FULL","allowedKinds":["TIMED"],
             "capacity":2,"slotMinutes":30,"minDurationMinutes":30,"maxDurationMinutes":480,"horizonDays":180,"version":2}
            """;

    private FindCalendarDefinition find;
    private SaveCalendarDefinition save;
    private DeleteCalendarDefinition delete;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        find = mock(FindCalendarDefinition.class);
        save = mock(SaveCalendarDefinition.class);
        delete = mock(DeleteCalendarDefinition.class);
        mvc = MockMvcBuilders.standaloneSetup(new CalendarDefinitionEndpoint(find, save, delete, new CalendarDefinitionMapper()))
                .setControllerAdvice(new CalendarApiExceptionHandler())
                .build();
    }

    private static CalendarDefinition stored() {
        CalendarDefinition definition = CalendarDefinitionTest.definition("team-calendar");
        definition.setDescription("What the team does");
        definition.setReadRoles(new ArrayList<>(List.of("staff")));
        definition.setVersion(2L);
        definition.setCreatedAt(Instant.parse("2026-11-19T10:00:00Z"));
        definition.setUpdatedAt(Instant.parse("2026-11-19T11:00:00Z"));
        return definition;
    }

    @Test
    void listsTheDraftsByDefaultAndTheReleasedRowsOnRequest() throws Exception {
        when(find.findAll("org-1", Channel.DRAFT)).thenReturn(List.of(stored()));
        when(find.findAll("org-1", Channel.RELEASED)).thenReturn(List.of(stored(), stored()));

        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value("team-calendar"))
                .andExpect(jsonPath("$[0].name").value("Team calendar"))
                .andExpect(jsonPath("$[0].kind").value("TEAM"))
                .andExpect(jsonPath("$[0].visibility").value("AUTHENTICATED"))
                .andExpect(jsonPath("$[0].publicDetail").value("BUSY_ONLY"))
                .andExpect(jsonPath("$[0].allowedKinds[0]").value("TIMED"))
                .andExpect(jsonPath("$[0].allowedKinds[1]").value("ALL_DAY"))
                .andExpect(jsonPath("$[0].readRoles[0]").value("staff"))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].createdAt").exists());
        mvc.perform(get(BASE).param("revision", "released")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getsOneDefinitionAndAnswers404ForAnUnknownKey() throws Exception {
        when(find.find("org-1", "team-calendar", Channel.DRAFT)).thenReturn(stored());
        when(find.find("org-1", "nope", Channel.DRAFT)).thenThrow(new CalendarDefinitionNotFoundException("org-1", "nope"));

        mvc.perform(get(BASE + "/team-calendar")).andExpect(status().isOk()).andExpect(jsonPath("$.key").value("team-calendar"));
        mvc.perform(get(BASE + "/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorId").value("calendar.definition.not-found"));
    }

    @Test
    void anUnknownRevisionIsABadRequest() throws Exception {
        mvc.perform(get(BASE).param("revision", "latest")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorId").value("calendar.definition.invalid-revision"));
    }

    @Test
    void savesTranslatingTheInputIntoTheDomain() throws Exception {
        when(save.save(eq("org-1"), eq("meeting-room-a"), any(CalendarDefinition.class))).thenReturn(stored());

        mvc.perform(put(BASE + "/meeting-room-a").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.key").value("team-calendar"));

        ArgumentCaptor<CalendarDefinition> desired = ArgumentCaptor.forClass(CalendarDefinition.class);
        verify(save).save(eq("org-1"), eq("meeting-room-a"), desired.capture());
        CalendarDefinition input = desired.getValue();
        assertThat(input.getVisibility()).isEqualTo(CalendarVisibility.ROLES);
        assertThat(input.getReadRoles()).containsExactly("staff");
        assertThat(input.getBookRoles()).containsExactly("staff");
        assertThat(input.getManageRoles()).containsExactly("office-admin");
        assertThat(input.getCapacity()).isEqualTo(2);
        assertThat(input.getSlotMinutes()).isEqualTo(30);
        assertThat(input.getHorizonDays()).isEqualTo(180);
        assertThat(input.getVersion()).isEqualTo(2L);
    }

    @Test
    void aBodyThatSaysNothingGetsTheCautiousDefaultsNeverAnOpenOne() throws Exception {
        when(save.save(any(), any(), any(CalendarDefinition.class))).thenReturn(stored());

        mvc.perform(put(BASE + "/team-calendar").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Team\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CalendarDefinition> desired = ArgumentCaptor.forClass(CalendarDefinition.class);
        verify(save).save(any(), any(), desired.capture());
        assertThat(desired.getValue().getVisibility()).isEqualTo(CalendarVisibility.AUTHENTICATED);
        assertThat(desired.getValue().getPublicDetail()).isEqualTo(PublicDetail.BUSY_ONLY);
    }

    @Test
    void mapsAnInvalidDefinitionTo400AndAStaleWriteTo409() throws Exception {
        when(save.save(any(), eq("bad"), any())).thenThrow(new InvalidCalendarDefinitionException("bad", List.of("'name' is required")));
        when(save.save(any(), eq("stale"), any())).thenThrow(new StaleCalendarDefinitionException("stale"));

        mvc.perform(put(BASE + "/bad").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorId").value("calendar.definition.invalid"));
        mvc.perform(put(BASE + "/stale").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").value("calendar.stale-write"));
    }

    @Test
    void deletesTheDraft() throws Exception {
        mvc.perform(delete(BASE + "/team-calendar")).andExpect(status().isNoContent());

        verify(delete).delete("org-1", "team-calendar");
    }

}
