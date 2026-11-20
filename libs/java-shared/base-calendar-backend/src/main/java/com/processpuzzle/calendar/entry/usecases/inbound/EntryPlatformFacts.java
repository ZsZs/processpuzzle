package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes the {@link PlatformEvent}s about entries — the raw facts base-event matches against the organization's
 * catalog, so a workflow can start when something is booked. Durable, through the host's event publication
 * registry; they are published in the booking's own transaction, so a rolled-back booking publishes nothing.
 */
@Component
public class EntryPlatformFacts {

    /** The {@code subjectType} of every fact published here. */
    public static final String SUBJECT_TYPE = "calendar-entry";

    /** The payload attributes of the facts about an entry — what a catalogued event may declare as parameters. */
    public static final Set<String> FACT_ATTRIBUTES = Set.of("calendarKey", "kind", "title", "visibility");

    private final ApplicationEventPublisher events;

    public EntryPlatformFacts(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void created(CalendarEntry entry) {
        publish(entry, PlatformEventAction.CREATED);
    }

    public void updated(CalendarEntry entry) {
        publish(entry, PlatformEventAction.UPDATED);
    }

    public void deleted(CalendarEntry entry) {
        publish(entry, PlatformEventAction.DELETED);
    }

    private void publish(CalendarEntry entry, PlatformEventAction action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("calendarKey", entry.getCalendarKey());
        payload.put("kind", entry.getKind().name());
        payload.put("title", entry.getTitle());
        payload.put("visibility", entry.getVisibility().name());
        events.publishEvent(PlatformEvent.of(entry.getOrgKey(), SUBJECT_TYPE, entry.getId().toString(), action, payload,
                Instant.now()));
    }
}
