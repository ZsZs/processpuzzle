package com.processpuzzle.calendar.entry.domain;

/** One occurrence of an entry as one caller sees it: the entry's view and the span of this occurrence. */
public record OccurrenceView(EntryView view, OccurrenceSpan span) {
}
