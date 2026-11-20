package com.processpuzzle.calendar.entry.domain;

/** How much of an entry others see. */
public enum EntryVisibility {
    /** Shown at the detail the calendar allows its readers. */
    PUBLIC,
    /** Shown as busy time to everyone but the organizer and the calendar's managers. */
    PRIVATE
}
