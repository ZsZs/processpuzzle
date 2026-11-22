package com.processpuzzle.calendar.entry.domain;

/**
 * One invitation as the caller asks for it. For a USER the principal is a subject the directory must know; the name is
 * looked up there rather than taken from the request, so an invitation can never carry a name nobody checked.
 */
public record AttendeeRequest(AttendeeType type, String principal, boolean required) {
}
