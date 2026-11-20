package com.processpuzzle.calendar.definition.domain;

/**
 * The kinds of booking. Lives with the definition because a definition names the kinds its calendar accepts.
 */
public enum EntryKind {
    /** A start and an end instant. */
    TIMED,
    /** A start date and an exclusive end date, in the organization's calendar. */
    ALL_DAY,
    /** A TIMED or ALL_DAY template repeated by a preset recurrence. */
    RECURRING
}
