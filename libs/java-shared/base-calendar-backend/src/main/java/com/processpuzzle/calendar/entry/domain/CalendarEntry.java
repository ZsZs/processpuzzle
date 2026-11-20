package com.processpuzzle.calendar.entry.domain;

import com.processpuzzle.calendar.definition.domain.EntryKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One booking on one calendar. A timed entry is a UTC instant range plus the IANA zone it was entered in; an
 * all-day entry is a local date range whose end is exclusive and which has no zone. An entry belongs to the
 * calendar by key only — the calendar's rules are read from its released definition whenever they matter.
 *
 * <p>The organizer is recorded as the identity provider's subject and a display-name snapshot, since names cannot
 * be looked up later. The subject is never sent to a client.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "calendarKey", "kind", "title"})
@Entity
@Table(name = "calendar_entry", indexes = {
        @Index(name = "idx_calendar_entry_timed", columnList = "org_key, calendar_key, start_utc"),
        @Index(name = "idx_calendar_entry_all_day", columnList = "org_key, calendar_key, start_date"),
        @Index(name = "idx_calendar_entry_organizer", columnList = "org_key, organizer_sub")})
public class CalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Column(name = "calendar_key", nullable = false)
    private String calendarKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(length = 200)
    private String location;

    @Column(length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private EntryVisibility visibility = EntryVisibility.PUBLIC;

    /** TIMED: the first instant. */
    @Column(name = "start_utc")
    private Instant startUtc;

    /** TIMED: the instant the entry ends, exclusive. */
    @Column(name = "end_utc")
    private Instant endUtc;

    /** TIMED: the IANA zone the entry was entered in. */
    @Column(name = "time_zone", length = 64)
    private String timeZone;

    /** ALL_DAY: the first day. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** ALL_DAY: the day after the last day. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "organizer_sub", nullable = false)
    private String organizerSub;

    @Column(name = "organizer_display_name")
    private String organizerDisplayName;

    /** Who is invited, the organizer included. Loaded with the entry: an entry is never read without who it concerns. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "calendar_entry_attendee", joinColumns = @JoinColumn(name = "entry_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "attendee_type", "principal"}))
    @Builder.Default
    private List<Attendee> attendees = new ArrayList<>();

    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * A new entry from a request that already passed {@link BookingRules}. Only the fields of the request's kind are
     * kept; a timed entry without a zone takes {@code defaultZone}, the organization's.
     */
    public static CalendarEntry of(String orgKey, String calendarKey, BookingRequest request, ZoneId defaultZone) {
        CalendarEntry entry = CalendarEntry.builder()
                .orgKey(orgKey)
                .calendarKey(calendarKey)
                .kind(request.kind())
                .title(request.title().strip())
                .description(blankToNull(request.description()))
                .location(blankToNull(request.location()))
                .category(blankToNull(request.category()))
                .visibility(request.visibility() == null ? EntryVisibility.PUBLIC : request.visibility())
                .build();
        if (request.kind() == EntryKind.TIMED) {
            entry.startUtc = request.start();
            entry.endUtc = request.end();
            entry.timeZone = request.timeZone() == null || request.timeZone().isBlank() ? defaultZone.getId() : request.timeZone();
        } else {
            entry.startDate = request.startDate();
            entry.endDate = request.endDate();
        }
        return entry;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Whether {@code subject} is the person who booked the entry. */
    public boolean isOrganizedBy(String subject) {
        return subject != null && subject.equals(organizerSub);
    }

    /** The attendees, never null; a row stored before the table existed reads as nobody. */
    public List<Attendee> getAttendees() {
        attendees = attendees == null ? new ArrayList<>() : attendees;
        return attendees;
    }

    /**
     * Whether the caller is invited: by name, or through one of their roles. The organizer is invited too, so an
     * organizer who wrote the entry is never a stranger to it.
     */
    public boolean isInvited(String subject, Collection<String> roles) {
        return getAttendees().stream().anyMatch(attendee -> attendee.isUser(subject) || attendee.isRole(roles));
    }

    /** The caller's own invitation, if they have one by name. */
    public Optional<Attendee> invitationOf(String subject) {
        return getAttendees().stream().filter(attendee -> attendee.isUser(subject)).findFirst();
    }

    /**
     * Makes the attendee list the one the organizer asked for. The organizer stays on it, accepted; an invitee who is
     * still on the list keeps the answer they gave, unless the time moved, in which case every answer is asked again.
     * A new invitee starts waiting. Nothing else about the entry changes here.
     *
     * @param desired the invitees the caller named, without the organizer
     * @param organizer the organizer's subject and name snapshot
     * @param timeMoved whether the start or the end changed
     */
    public void reconcileAttendees(List<Attendee> desired, Attendee organizer, boolean timeMoved, Instant now) {
        List<Attendee> kept = new ArrayList<>();
        for (Attendee wanted : desired) {
            if (wanted.equals(organizer)) continue;
            Attendee existing = getAttendees().stream().filter(current -> current.equals(wanted)).findFirst().orElse(null);
            if (existing != null && !timeMoved) {
                kept.add(existing);
            } else if (existing != null) {
                existing.forgetResponse();
                kept.add(existing);
            } else {
                kept.add(wanted.getType() == AttendeeType.USER
                        ? Attendee.user(wanted.getPrincipal(), wanted.getDisplayName(), wanted.isRequired(), AttendanceResponse.NEEDS_ACTION, now)
                        : wanted);
            }
        }
        Attendee organizerRow = getAttendees().stream().filter(current -> current.equals(organizer)).findFirst()
                .orElse(organizer);
        organizerRow.setDisplayName(organizer.getDisplayName());
        organizerRow.setResponse(AttendanceResponse.ACCEPTED);
        organizerRow.setRespondedAt(now);
        kept.add(0, organizerRow);
        getAttendees().clear();
        getAttendees().addAll(kept);
    }

    /** Whether the start or the end of {@code other} differ from this entry's: the change that asks invitees again. */
    public boolean timeDiffersFrom(CalendarEntry other) {
        return !java.util.Objects.equals(startUtc, other.startUtc) || !java.util.Objects.equals(endUtc, other.endUtc)
                || !java.util.Objects.equals(startDate, other.startDate) || !java.util.Objects.equals(endDate, other.endDate);
    }

    /** When the entry starts, for ordering: a timed entry's instant, an all-day entry's midnight in {@code zone}. */
    public Instant startsAt(ZoneId zone) {
        return kind == EntryKind.ALL_DAY ? startDate.atStartOfDay(zone).toInstant() : startUtc;
    }

    /** Copies everything the organizer edits from {@code source}, leaving identity, organizer, version and timestamps. */
    public void replaceWith(CalendarEntry source) {
        this.title = source.title;
        this.description = source.description;
        this.location = source.location;
        this.category = source.category;
        this.visibility = source.visibility;
        this.startUtc = source.startUtc;
        this.endUtc = source.endUtc;
        this.timeZone = source.timeZone;
        this.startDate = source.startDate;
        this.endDate = source.endDate;
    }
}
