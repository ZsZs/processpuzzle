package com.processpuzzle.calendar.entry.adapters.inbound;

import com.processpuzzle.calendar.api.CalendarPrivacyApi;
import com.processpuzzle.calendar.entry.usecases.inbound.EraseCalendarUser;
import com.processpuzzle.calendar.model.ErasureResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code CalendarPrivacyApi}: erasing a user's calendar data. */
@RestController
public class CalendarPrivacyEndpoint implements CalendarPrivacyApi {

    private final EraseCalendarUser erase;

    public CalendarPrivacyEndpoint(EraseCalendarUser erase) {
        this.erase = erase;
    }

    @Override
    public ResponseEntity<ErasureResult> eraseCalendarUser(String orgKey, String userSub) {
        EraseCalendarUser.Result result = erase.erase(orgKey, userSub);
        return ResponseEntity.ok(new ErasureResult().entries(result.entries()).invitations(result.invitations()));
    }
}
