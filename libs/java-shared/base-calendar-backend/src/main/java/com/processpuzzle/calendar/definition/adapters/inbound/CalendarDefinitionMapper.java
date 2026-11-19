package com.processpuzzle.calendar.definition.adapters.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarKind;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.model.CalendarDefinitionInput;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Between the generated {@code com.processpuzzle.calendar.model} DTOs and the domain. The enums are separate
 * vocabularies on each side and are translated by name. A field the input leaves out takes the domain's
 * cautious default, never an open one.
 */
@Component
public class CalendarDefinitionMapper {

    public CalendarDefinition toDomain(CalendarDefinitionInput input) {
        return CalendarDefinition.builder()
                .name(input.getName())
                .description(input.getDescription())
                .kind(input.getKind() == null ? CalendarKind.TEAM : CalendarKind.valueOf(input.getKind().name()))
                .visibility(input.getVisibility() == null
                        ? CalendarVisibility.AUTHENTICATED : CalendarVisibility.valueOf(input.getVisibility().name()))
                .readRoles(copy(input.getReadRoles()))
                .bookRoles(copy(input.getBookRoles()))
                .manageRoles(copy(input.getManageRoles()))
                .publicDetail(input.getPublicDetail() == null
                        ? PublicDetail.BUSY_ONLY : PublicDetail.valueOf(input.getPublicDetail().name()))
                .allowedKinds(kinds(input.getAllowedKinds()))
                .capacity(input.getCapacity() == null ? 1 : input.getCapacity())
                .slotMinutes(input.getSlotMinutes())
                .minDurationMinutes(input.getMinDurationMinutes())
                .maxDurationMinutes(input.getMaxDurationMinutes())
                .horizonDays(input.getHorizonDays())
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.calendar.model.CalendarDefinition toModel(CalendarDefinition definition) {
        return new com.processpuzzle.calendar.model.CalendarDefinition()
                .key(definition.getDefinitionKey())
                .name(definition.getName())
                .description(definition.getDescription())
                .kind(com.processpuzzle.calendar.model.CalendarKind.valueOf(definition.getKind().name()))
                .visibility(com.processpuzzle.calendar.model.CalendarVisibility.valueOf(definition.getVisibility().name()))
                .readRoles(new ArrayList<>(definition.getReadRoles()))
                .bookRoles(new ArrayList<>(definition.getBookRoles()))
                .manageRoles(new ArrayList<>(definition.getManageRoles()))
                .publicDetail(com.processpuzzle.calendar.model.PublicDetail.valueOf(definition.getPublicDetail().name()))
                .allowedKinds(definition.effectiveKinds().stream()
                        .map(kind -> com.processpuzzle.calendar.model.EntryKind.valueOf(kind.name())).toList())
                .capacity(definition.getCapacity())
                .slotMinutes(definition.getSlotMinutes())
                .minDurationMinutes(definition.getMinDurationMinutes())
                .maxDurationMinutes(definition.getMaxDurationMinutes())
                .horizonDays(definition.getHorizonDays())
                .version(definition.getVersion())
                .createdAt(offset(definition.getCreatedAt()))
                .updatedAt(offset(definition.getUpdatedAt()));
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static List<EntryKind> kinds(List<com.processpuzzle.calendar.model.EntryKind> values) {
        return values == null
                ? new ArrayList<>()
                : new ArrayList<>(values.stream().map(kind -> kind == null ? null : EntryKind.valueOf(kind.name())).toList());
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
