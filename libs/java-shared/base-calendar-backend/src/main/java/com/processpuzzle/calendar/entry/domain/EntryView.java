package com.processpuzzle.calendar.entry.domain;

/**
 * An entry as one caller sees it: the stored entry plus what that caller is allowed to read of it and do with it.
 * The endpoint's mapper hides the fields of a {@code busy} view; the use case that builds it decides, so the rule
 * lives in one place.
 */
public record EntryView(CalendarEntry entry, boolean busy, boolean mine, boolean canEdit) {
}
