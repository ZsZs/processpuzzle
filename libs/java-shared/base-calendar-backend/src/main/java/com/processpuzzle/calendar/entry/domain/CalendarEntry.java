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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    /** RECURRING: the preset the template repeats by; null for a single entry. */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 16)
    private Recurrence.Frequency recurrenceFrequency;

    @Column(name = "recurrence_count")
    private Integer recurrenceCount;

    @Column(name = "recurrence_until")
    private LocalDate recurrenceUntil;

    /** RECURRING: the day of the template's first occurrence, in the entry's zone. */
    @Column(name = "series_first_day")
    private LocalDate seriesFirstDay;

    /** RECURRING: the day of the last occurrence, null for a series that never ends. Lets a window query skip series. */
    @Column(name = "series_last_day")
    private LocalDate seriesLastDay;

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
        if (entry.hasTimedTemplate(request)) {
            entry.startUtc = request.start();
            entry.endUtc = request.end();
            entry.timeZone = request.timeZone() == null || request.timeZone().isBlank() ? defaultZone.getId() : request.timeZone();
        } else {
            entry.startDate = request.startDate();
            entry.endDate = request.endDate();
        }
        if (request.kind() == EntryKind.RECURRING) {
            entry.keepRecurrence(request.recurrence());
        }
        return entry;
    }

    /** A recurring entry's template is timed when the request gives instants, and all-day when it gives dates. */
    private boolean hasTimedTemplate(BookingRequest request) {
        return request.kind() == EntryKind.TIMED || request.kind() == EntryKind.RECURRING && request.start() != null;
    }

    private void keepRecurrence(Recurrence rule) {
        this.recurrenceFrequency = rule.frequency();
        this.recurrenceCount = rule.count();
        this.recurrenceUntil = rule.until();
        this.seriesFirstDay = isAllDay() ? startDate : startUtc.atZone(ZoneId.of(timeZone)).toLocalDate();
        this.seriesLastDay = rule.lastDay(seriesFirstDay);
    }

    /** The repetition of a RECURRING entry, or null for any other kind. */
    public Recurrence recurrence() {
        return recurrenceFrequency == null ? null : new Recurrence(recurrenceFrequency, recurrenceCount, recurrenceUntil);
    }

    public boolean isRecurring() {
        return kind == EntryKind.RECURRING;
    }

    /** Whether the entry, or a recurring entry's template, is all-day rather than timed. */
    public boolean isAllDay() {
        return kind == EntryKind.ALL_DAY || (kind == EntryKind.RECURRING && startUtc == null);
    }

    /**
     * The occurrences that may overlap {@code [from, to)}, in start order. A single entry is its own one occurrence. A
     * recurring entry expands to the days of its series within the window, each keeping the template's local time in
     * its zone, so a weekly 09:00 stays 09:00 across a change of daylight saving time.
     */
    public List<OccurrenceSpan> spansWithin(Instant from, Instant to, ZoneId organizationZone) {
        if (kind != EntryKind.RECURRING) {
            return List.of(new OccurrenceSpan(null, startUtc, endUtc, startDate, endDate));
        }
        // One day either side: a timed occurrence's local day is in the entry's zone, which may differ from the organization's.
        LocalDate first = from.atZone(organizationZone).toLocalDate().minusDays(1);
        LocalDate last = to.minusNanos(1).atZone(organizationZone).toLocalDate().plusDays(1);
        return recurrence().daysWithin(seriesFirstDay, first, last).stream().map(this::occurrenceOn).toList();
    }

    private OccurrenceSpan occurrenceOn(LocalDate day) {
        if (isAllDay()) {
            return new OccurrenceSpan(day, null, null, day, day.plusDays(ChronoUnit.DAYS.between(startDate, endDate)));
        }
        ZoneId zone = ZoneId.of(timeZone);
        Instant start = day.atTime(startUtc.atZone(zone).toLocalTime()).atZone(zone).toInstant();
        return new OccurrenceSpan(day, start, start.plus(Duration.between(startUtc, endUtc)), null, null);
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
        return !Objects.equals(startUtc, other.startUtc) || !Objects.equals(endUtc, other.endUtc)
                || !Objects.equals(startDate, other.startDate) || !Objects.equals(endDate, other.endDate)
                || !Objects.equals(recurrence(), other.recurrence());
    }

    /** When the entry starts, for ordering: a timed entry's instant, an all-day entry's midnight in {@code zone}. */
    public Instant startsAt(ZoneId zone) {
        return isAllDay() ? startDate.atStartOfDay(zone).toInstant() : startUtc;
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
        this.recurrenceFrequency = source.recurrenceFrequency;
        this.recurrenceCount = source.recurrenceCount;
        this.recurrenceUntil = source.recurrenceUntil;
        this.seriesFirstDay = source.seriesFirstDay;
        this.seriesLastDay = source.seriesLastDay;
    }
}
