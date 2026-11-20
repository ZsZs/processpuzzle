package com.processpuzzle.calendar.entry.domain;

/**
 * A USER attendee's answer. {@link #NEEDS_ACTION} until the invitee answers, and again after the entry's time moves.
 * A ROLE attendee has no answer at all.
 */
public enum AttendanceResponse {
    NEEDS_ACTION,
    ACCEPTED,
    TENTATIVE,
    DECLINED
}
