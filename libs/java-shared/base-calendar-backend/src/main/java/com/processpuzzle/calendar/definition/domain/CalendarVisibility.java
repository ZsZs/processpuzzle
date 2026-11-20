package com.processpuzzle.calendar.definition.domain;

/** Who may read a calendar. */
public enum CalendarVisibility {
    /** Anybody, anonymous callers included. */
    PUBLIC,
    /** Any signed-in member of the organization. */
    AUTHENTICATED,
    /** Signed-in members holding one of the definition's read roles. */
    ROLES
}
