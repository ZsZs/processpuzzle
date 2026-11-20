package com.processpuzzle.calendar.entry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One invitation on an entry: a person ({@link AttendeeType#USER}), identified by the identity provider's subject, or
 * a role. The display name is a snapshot taken when the invitation is written, as the organizer's is: names cannot be
 * looked up later, and the directory is not asked again when an entry is read.
 *
 * <p>Identity is the pair of type and principal within an entry. The organizer is one of them, with
 * {@link AttendanceResponse#ACCEPTED}, so that the organizer's own entry lists them among the people it concerns.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"type", "principal"})
@ToString(of = {"type", "principal", "response"})
@Embeddable
public class Attendee {

    @Enumerated(EnumType.STRING)
    @Column(name = "attendee_type", nullable = false, length = 8)
    private AttendeeType type;

    /** A USER's subject, or a ROLE's name. */
    @Column(nullable = false, length = 200)
    private String principal;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = false;

    /** The answer of a USER; null for a ROLE, which answers nothing. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private AttendanceResponse response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** A person invited by name, not yet answered (or the organizer, who is accepted from the start). */
    public static Attendee user(String subject, String displayName, boolean required, AttendanceResponse response, Instant at) {
        return new Attendee(AttendeeType.USER, subject, displayName, required, response, response == AttendanceResponse.NEEDS_ACTION ? null : at);
    }

    /** Every holder of a realm role; it answers nothing, so it has no response. */
    public static Attendee role(String roleName, boolean required) {
        return new Attendee(AttendeeType.ROLE, roleName, roleName, required, null, null);
    }

    public boolean isUser(String subject) {
        return type == AttendeeType.USER && principal.equals(subject);
    }

    public boolean isRole(java.util.Collection<String> roles) {
        return type == AttendeeType.ROLE && roles.contains(principal);
    }

    /** Puts the invitee back to waiting for an answer. */
    public void forgetResponse() {
        if (type == AttendeeType.USER) {
            response = AttendanceResponse.NEEDS_ACTION;
            respondedAt = null;
        }
    }

    /** Records an answer. Only a USER answers; the caller checks who is answering. */
    public void answer(AttendanceResponse answer, Instant at) {
        response = answer;
        respondedAt = at;
    }
}
