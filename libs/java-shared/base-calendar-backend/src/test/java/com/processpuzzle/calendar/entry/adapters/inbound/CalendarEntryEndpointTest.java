package com.processpuzzle.calendar.entry.adapters.inbound;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.timedEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.calendar.common.CalendarApiExceptionHandler;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.exception.StaleEntryException;
import com.processpuzzle.calendar.entry.usecases.inbound.BookEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.CancelEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.GetEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.UpdateEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of entries: status codes, the shape of the bodies, and what a busy view leaves out. */
class CalendarEntryEndpointTest {

    private static final String BASE = "/organizations/org-1/calendar/calendars/team-calendar/entries";
    private static final String TIMED_BODY = """
            {"kind":"TIMED","title":"Planning","description":"Quarterly","location":"Room 1","category":"meeting",
             "visibility":"PRIVATE","start":"2026-12-01T10:00:00+01:00","end":"2026-12-01T11:00:00+01:00",
             "timeZone":"Europe/Budapest","version":3}
            """;

    private BookEntry book;
    private UpdateEntry update;
    private CancelEntry cancel;
    private GetEntry get;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        book = mock(BookEntry.class);
        update = mock(UpdateEntry.class);
        cancel = mock(CancelEntry.class);
        get = mock(GetEntry.class);
        mvc = MockMvcBuilders.standaloneSetup(new CalendarEntryEndpoint(book, update, cancel, get, new EntryMapper()))
                .setControllerAdvice(new CalendarApiExceptionHandler())
                .build();
    }

    private static EntryView full(CalendarEntry entry) {
        return new EntryView(entry, false, true, true);
    }

    @Test
    void booksTranslatingTheInputAndAnswers201WithTheEntry() throws Exception {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PRIVATE);
        when(book.book(eq(ORG), eq("team-calendar"), any(BookingRequest.class))).thenReturn(full(stored));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(TIMED_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(stored.getId().toString()))
                .andExpect(jsonPath("$.kind").value("TIMED"))
                .andExpect(jsonPath("$.busy").value(false))
                .andExpect(jsonPath("$.title").value("Planning"))
                .andExpect(jsonPath("$.organizerDisplayName").value("Name of sub-alice"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.version").value(0));

        ArgumentCaptor<BookingRequest> request = ArgumentCaptor.forClass(BookingRequest.class);
        verify(book).book(eq(ORG), eq("team-calendar"), request.capture());
        assertThat(request.getValue().kind()).isEqualTo(EntryKind.TIMED);
        assertThat(request.getValue().visibility()).isEqualTo(EntryVisibility.PRIVATE);
        assertThat(request.getValue().start()).isEqualTo(Instant.parse("2026-12-01T09:00:00Z"));
        assertThat(request.getValue().end()).isEqualTo(Instant.parse("2026-12-01T10:00:00Z"));
        assertThat(request.getValue().timeZone()).isEqualTo("Europe/Budapest");
    }

    @Test
    void aBodyWithoutVisibilityIsPublicAndAMalformedOneIsABadRequest() throws Exception {
        when(book.book(any(), any(), any())).thenReturn(full(timedEntry(ALICE, EntryVisibility.PUBLIC)));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"TIMED\",\"title\":\"x\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<BookingRequest> request = ArgumentCaptor.forClass(BookingRequest.class);
        verify(book).book(any(), any(), request.capture());
        assertThat(request.getValue().visibility()).isEqualTo(EntryVisibility.PUBLIC);

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"no kind\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"SOMETIMES\",\"title\":\"x\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void mapsTheRefusalsToTheirStatusCodes() throws Exception {
        when(book.book(any(), eq("denied"), any())).thenThrow(new CalendarAccessDeniedException("You may not book on this calendar"));
        when(book.book(any(), eq("hidden"), any())).thenThrow(new CalendarNotFoundException());
        when(book.book(any(), eq("rules"), any())).thenThrow(new InvalidBookingException(List.of("'end' must be after 'start'")));

        mvc.perform(post("/organizations/org-1/calendar/calendars/denied/entries").contentType(MediaType.APPLICATION_JSON).content(TIMED_BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorId").value("calendar.access-denied"));
        mvc.perform(post("/organizations/org-1/calendar/calendars/hidden/entries").contentType(MediaType.APPLICATION_JSON).content(TIMED_BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorId").value("calendar.not-found"));
        mvc.perform(post("/organizations/org-1/calendar/calendars/rules/entries").contentType(MediaType.APPLICATION_JSON).content(TIMED_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorId").value("calendar.booking.invalid"))
                .andExpect(jsonPath("$.errorText").value(org.hamcrest.Matchers.containsString("'end' must be after 'start'")));
    }

    @Test
    void aBusyEntryCarriesTheTimesAndNothingElse() throws Exception {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PRIVATE);
        when(get.get(ORG, "team-calendar", stored.getId())).thenReturn(new EntryView(stored, true, false, false));

        mvc.perform(get(BASE + "/" + stored.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busy").value(true))
                .andExpect(jsonPath("$.start").exists())
                .andExpect(jsonPath("$.end").exists())
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.location").value(nullValue()))
                .andExpect(jsonPath("$.category").value(nullValue()))
                .andExpect(jsonPath("$.organizerDisplayName").value(nullValue()))
                .andExpect(jsonPath("$.visibility").value(nullValue()))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void theOrganizerSubjectIsNeverSerialized() throws Exception {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        stored.setOrganizerDisplayName("Alice A.");
        when(get.get(ORG, "team-calendar", stored.getId())).thenReturn(full(stored));

        String body = mvc.perform(get(BASE + "/" + stored.getId())).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Alice A.").doesNotContain(ALICE).doesNotContain("organizerSub");
    }

    @Test
    void answers404ForAMissingEntry() throws Exception {
        UUID missing = UUID.randomUUID();
        when(get.get(ORG, "team-calendar", missing)).thenThrow(new EntryNotFoundException());

        mvc.perform(get(BASE + "/" + missing)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorId").value("calendar.entry.not-found"));
    }

    @Test
    void updatesAndMapsAStaleVersionTo409() throws Exception {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        UUID stale = UUID.randomUUID();
        when(update.update(eq(ORG), eq("team-calendar"), eq(stored.getId()), any())).thenReturn(full(stored));
        when(update.update(eq(ORG), eq("team-calendar"), eq(stale), any())).thenThrow(new StaleEntryException());

        mvc.perform(put(BASE + "/" + stored.getId()).contentType(MediaType.APPLICATION_JSON).content(TIMED_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Planning"));
        mvc.perform(put(BASE + "/" + stale).contentType(MediaType.APPLICATION_JSON).content(TIMED_BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").value("calendar.stale-write"));

        ArgumentCaptor<BookingRequest> request = ArgumentCaptor.forClass(BookingRequest.class);
        verify(update).update(eq(ORG), eq("team-calendar"), eq(stored.getId()), request.capture());
        assertThat(request.getValue().version()).isEqualTo(3L);
    }

    @Test
    void cancelsAnEntry() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());

        verify(cancel).cancel(ORG, "team-calendar", id);
    }

    @Test
    void anEntryIdThatIsNotAUuidIsABadRequest() throws Exception {
        mvc.perform(get(BASE + "/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE)).andExpect(status().isMethodNotAllowed());
    }
}
