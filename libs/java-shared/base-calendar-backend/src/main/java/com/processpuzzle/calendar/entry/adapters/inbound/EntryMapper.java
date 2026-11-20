package com.processpuzzle.calendar.entry.adapters.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.inbound.GetCalendar;
import com.processpuzzle.calendar.model.CalendarEntryInput;
import com.processpuzzle.calendar.model.CalendarPermissions;
import com.processpuzzle.calendar.model.CalendarView;
import com.processpuzzle.calendar.model.Occurrence;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Between the generated {@code com.processpuzzle.calendar.model} DTOs and the domain. The enums are separate
 * vocabularies on each side and are translated by name.
 *
 * <p>The content of an entry the caller sees as busy is left out here, from the {@link EntryView} the use case
 * decided on; the mapper never decides who may see what. The organizer's subject is not part of any DTO.
 */
@Component
public class EntryMapper {

    public BookingRequest toRequest(CalendarEntryInput input) {
        return new BookingRequest(
                input.getKind() == null ? null : EntryKind.valueOf(input.getKind().name()),
                input.getTitle(),
                input.getDescription(),
                input.getLocation(),
                input.getCategory(),
                input.getVisibility() == null ? EntryVisibility.PUBLIC : EntryVisibility.valueOf(input.getVisibility().name()),
                instant(input.getStart()),
                instant(input.getEnd()),
                input.getTimeZone(),
                input.getStartDate(),
                input.getEndDate(),
                input.getVersion());
    }

    public com.processpuzzle.calendar.model.CalendarEntry toModel(EntryView view) {
        CalendarEntry entry = view.entry();
        com.processpuzzle.calendar.model.CalendarEntry model = new com.processpuzzle.calendar.model.CalendarEntry(
                entry.getId(), entry.getCalendarKey(), com.processpuzzle.calendar.model.EntryKind.valueOf(entry.getKind().name()),
                view.busy())
                .start(offset(entry.getStartUtc()))
                .end(offset(entry.getEndUtc()))
                .timeZone(entry.getTimeZone())
                .startDate(entry.getStartDate())
                .endDate(entry.getEndDate())
                .mine(view.mine())
                .canEdit(view.canEdit())
                .version(entry.getVersion())
                .createdAt(offset(entry.getCreatedAt()))
                .updatedAt(offset(entry.getUpdatedAt()));
        if (!view.busy()) {
            model.title(entry.getTitle())
                    .description(entry.getDescription())
                    .location(entry.getLocation())
                    .category(entry.getCategory())
                    .visibility(com.processpuzzle.calendar.model.EntryVisibility.valueOf(entry.getVisibility().name()))
                    .organizerDisplayName(entry.getOrganizerDisplayName());
        }
        return model;
    }

    public Occurrence toOccurrence(EntryView view) {
        CalendarEntry entry = view.entry();
        Occurrence occurrence = new Occurrence(entry.getId(), entry.getCalendarKey(), entry.getKind() == EntryKind.ALL_DAY, view.busy())
                .start(offset(entry.getStartUtc()))
                .end(offset(entry.getEndUtc()))
                .startDate(entry.getStartDate())
                .endDate(entry.getEndDate())
                .mine(view.mine())
                .canEdit(view.canEdit());
        if (!view.busy()) {
            occurrence.title(entry.getTitle()).location(entry.getLocation()).category(entry.getCategory());
        }
        return occurrence;
    }

    public CalendarView toView(GetCalendar.Summary summary) {
        CalendarDefinition definition = summary.definition();
        return new CalendarView(
                definition.getDefinitionKey(),
                definition.getName(),
                com.processpuzzle.calendar.model.CalendarKind.valueOf(definition.getKind().name()),
                summary.timeZone().getId(),
                new CalendarPermissions(summary.access().canBook(), summary.access().canManage()))
                .description(definition.getDescription())
                .allowedKinds(definition.effectiveKinds().stream()
                        .map(kind -> com.processpuzzle.calendar.model.EntryKind.valueOf(kind.name())).toList())
                .capacity(definition.getCapacity())
                .slotMinutes(definition.getSlotMinutes())
                .minDurationMinutes(definition.getMinDurationMinutes())
                .maxDurationMinutes(definition.getMaxDurationMinutes())
                .horizonDays(definition.getHorizonDays());
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
