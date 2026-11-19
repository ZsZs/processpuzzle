package com.processpuzzle.calendar.definition.domain;

/** What a reader who may not book sees of an entry. */
public enum PublicDetail {
    FULL,
    /** Only when the entry takes place: no title, description, location, category or organizer. */
    BUSY_ONLY
}
