package com.processpuzzle.calendar.entry.adapters.inbound;

import com.processpuzzle.calendar.api.CalendarsApi;
import com.processpuzzle.calendar.entry.usecases.inbound.GetCalendar;
import com.processpuzzle.calendar.entry.usecases.inbound.ListOccurrences;
import com.processpuzzle.calendar.model.CalendarView;
import com.processpuzzle.calendar.model.Occurrence;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code CalendarsApi}: a calendar as the caller sees it, and what calendars show in a window. */
@RestController
public class CalendarEndpoint implements CalendarsApi {

    private final GetCalendar getCalendar;
    private final ListOccurrences listOccurrences;
    private final EntryMapper mapper;

    public CalendarEndpoint(GetCalendar getCalendar, ListOccurrences listOccurrences, EntryMapper mapper) {
        this.getCalendar = getCalendar;
        this.listOccurrences = listOccurrences;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CalendarView> getCalendar(String orgKey, String calendarKey) {
        return ResponseEntity.ok(mapper.toView(getCalendar.get(orgKey, calendarKey)));
    }

    @Override
    public ResponseEntity<List<Occurrence>> listOccurrences(String orgKey, List<String> calendars, OffsetDateTime from,
                                                            OffsetDateTime to) {
        return ResponseEntity.ok(listOccurrences.list(orgKey, calendars, from.toInstant(), to.toInstant()).stream()
                .map(mapper::toOccurrence).toList());
    }
}
