package com.processpuzzle.calendar.definition.domain;

/**
 * The kinds of booking. Lives with the definition because a definition names the kinds its calendar accepts.
 * {@link #RECURRING} is declared so a definition can already allow it; entries of that kind are refused until
 * recurrence is built.
 */
public enum EntryKind {
    /** A start and an end instant. */
    TIMED,
    /** A start date and an exclusive end date, in the organization's calendar. */
    ALL_DAY,
    /** A template plus a recurrence rule. Not yet bookable. */
    RECURRING
}
