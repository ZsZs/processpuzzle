package com.processpuzzle.calendar.entry.adapters.inbound;

import com.processpuzzle.calendar.api.CalendarEntriesApi;
import com.processpuzzle.calendar.entry.domain.AttendanceResponse;
import com.processpuzzle.calendar.entry.usecases.inbound.BookEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.CancelEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.GetEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.RespondToInvitation;
import com.processpuzzle.calendar.entry.usecases.inbound.UpdateEntry;
import com.processpuzzle.calendar.model.CalendarEntry;
import com.processpuzzle.calendar.model.AttendanceAnswer;
import com.processpuzzle.calendar.model.CalendarEntryInput;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code CalendarEntriesApi}. */
@RestController
public class CalendarEntryEndpoint implements CalendarEntriesApi {

    private final BookEntry book;
    private final UpdateEntry update;
    private final CancelEntry cancel;
    private final GetEntry get;
    private final RespondToInvitation respond;
    private final EntryMapper mapper;

    public CalendarEntryEndpoint(BookEntry book, UpdateEntry update, CancelEntry cancel, GetEntry get,
                                 RespondToInvitation respond, EntryMapper mapper) {
        this.respond = respond;
        this.book = book;
        this.update = update;
        this.cancel = cancel;
        this.get = get;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CalendarEntry> bookEntry(String orgKey, String calendarKey, CalendarEntryInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toModel(book.book(orgKey, calendarKey, mapper.toRequest(input))));
    }

    @Override
    public ResponseEntity<CalendarEntry> getEntry(String orgKey, String calendarKey, UUID entryId) {
        return ResponseEntity.ok(mapper.toModel(get.get(orgKey, calendarKey, entryId)));
    }

    @Override
    public ResponseEntity<CalendarEntry> updateEntry(String orgKey, String calendarKey, UUID entryId, CalendarEntryInput input) {
        return ResponseEntity.ok(mapper.toModel(update.update(orgKey, calendarKey, entryId, mapper.toRequest(input))));
    }

    @Override
    public ResponseEntity<CalendarEntry> answerInvitation(String orgKey, String calendarKey, UUID entryId, AttendanceAnswer attendanceAnswer) {
        AttendanceResponse answer = AttendanceResponse.valueOf(attendanceAnswer.getResponse().name());
        return ResponseEntity.ok(mapper.toModel(respond.answer(orgKey, calendarKey, entryId, answer)));
    }

    @Override
    public ResponseEntity<Void> cancelEntry(String orgKey, String calendarKey, UUID entryId) {
        cancel.cancel(orgKey, calendarKey, entryId);
        return ResponseEntity.noContent().build();
    }
}
