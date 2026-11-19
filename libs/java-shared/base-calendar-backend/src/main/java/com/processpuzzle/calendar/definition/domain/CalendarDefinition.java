package com.processpuzzle.calendar.definition.domain;

import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A designer-authored calendar: what it is, who may see it, who may book, and the rules a booking keeps.
 *
 * <p>The three role lists name realm roles and are interpreted at access time; nothing about who may do what
 * is copied into an entry. The defaults are the cautious ones — visible to signed-in members only, and only as
 * busy time to a reader who may not book — so a definition that says nothing never exposes an entry to an
 * anonymous caller.
 *
 * <p>Like every definition it has a {@link Channel#DRAFT} row a designer edits and a {@link Channel#RELEASED}
 * one the running application reads.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"orgKey", "definitionKey", "channel"})
@ToString
@Entity
@Table(name = "calendar_definition")
@IdClass(CalendarDefinitionKey.class)
public class CalendarDefinition {

    /** Lower-case words joined by hyphens, as the key appears in URLs and seed files. */
    public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final int MINUTES_PER_DAY = 1440;

    /** What a calendar accepts when its definition names no kinds. */
    public static final List<EntryKind> DEFAULT_KINDS = List.of(EntryKind.TIMED, EntryKind.ALL_DAY);

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    /** Chosen by the author, unique per organization. */
    @Id
    @Column(name = "definition_key", nullable = false)
    private String definitionKey;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    @Builder.Default
    private Channel channel = Channel.DRAFT;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CalendarKind kind = CalendarKind.TEAM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CalendarVisibility visibility = CalendarVisibility.AUTHENTICATED;

    /** ROLES visibility only: the realm roles that may read. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> readRoles = new ArrayList<>();

    /** Realm roles that may book; empty means any member who may read. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> bookRoles = new ArrayList<>();

    /** Realm roles that may edit anybody's entries; empty means nobody but an entry's organizer and platform staff. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> manageRoles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PublicDetail publicDetail = PublicDetail.BUSY_ONLY;

    /** The kinds of entry the calendar accepts; empty means {@link #DEFAULT_KINDS}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<EntryKind> allowedKinds = new ArrayList<>();

    /** RESOURCE only: how many bookings may overlap. */
    @Column(nullable = false)
    @Builder.Default
    private int capacity = 1;

    /** A timed entry starts and ends on a multiple of this many minutes; null allows any time. */
    private Integer slotMinutes;

    private Integer minDurationMinutes;

    private Integer maxDurationMinutes;

    /** How many days ahead an entry may start; null for no limit. */
    private Integer horizonDays;

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

    // The lists are never null, also for a row stored before the column existed.

    public List<String> getReadRoles() {
        readRoles = readRoles == null ? new ArrayList<>() : readRoles;
        return readRoles;
    }

    public List<String> getBookRoles() {
        bookRoles = bookRoles == null ? new ArrayList<>() : bookRoles;
        return bookRoles;
    }

    public List<String> getManageRoles() {
        manageRoles = manageRoles == null ? new ArrayList<>() : manageRoles;
        return manageRoles;
    }

    public List<EntryKind> getAllowedKinds() {
        allowedKinds = allowedKinds == null ? new ArrayList<>() : allowedKinds;
        return allowedKinds;
    }

    /** The kinds the calendar accepts, with the default applied when none are named. */
    public List<EntryKind> effectiveKinds() {
        return getAllowedKinds().isEmpty() ? DEFAULT_KINDS : List.copyOf(getAllowedKinds());
    }

    public boolean accepts(EntryKind entryKind) {
        return effectiveKinds().contains(entryKind);
    }

    /** Every role this definition names, once each — what the organization's role catalog has to contain. */
    public Set<String> allRoles() {
        Set<String> roles = new LinkedHashSet<>();
        roles.addAll(getReadRoles());
        roles.addAll(getBookRoles());
        roles.addAll(getManageRoles());
        return roles;
    }

    /** @return the rule violations, empty when the definition is valid */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (definitionKey == null || !KEY_PATTERN.matcher(definitionKey).matches()) {
            errors.add("'key' must be lower-case words joined by hyphens, e.g. 'meeting-room-a'");
        }
        if (isBlank(name)) {
            errors.add("'name' is required");
        }
        if (kind == null || visibility == null || publicDetail == null) {
            errors.add("'kind', 'visibility' and 'publicDetail' are required");
            return errors;
        }
        if (visibility == CalendarVisibility.ROLES && getReadRoles().isEmpty()) {
            errors.add("a ROLES calendar needs at least one read role");
        }
        if (allRoles().stream().anyMatch(CalendarDefinition::isBlank)) {
            errors.add("roles must not be blank");
        }
        validateBookingRules(errors);
        return errors;
    }

    private void validateBookingRules(List<String> errors) {
        if (capacity < 1) {
            errors.add("'capacity' must be at least 1");
        }
        if (kind == CalendarKind.TEAM && capacity != 1) {
            errors.add("'capacity' only applies to a RESOURCE calendar");
        }
        if (slotMinutes != null && (slotMinutes < 1 || slotMinutes > MINUTES_PER_DAY)) {
            errors.add("'slotMinutes' must be between 1 and " + MINUTES_PER_DAY);
        }
        if (isNotPositive(minDurationMinutes) || isNotPositive(maxDurationMinutes)) {
            errors.add("'minDurationMinutes' and 'maxDurationMinutes' must be positive");
        } else if (minDurationMinutes != null && maxDurationMinutes != null && minDurationMinutes > maxDurationMinutes) {
            errors.add("'minDurationMinutes' must not exceed 'maxDurationMinutes'");
        }
        if (isNotPositive(horizonDays)) {
            errors.add("'horizonDays' must be positive");
        }
        if (getAllowedKinds().stream().anyMatch(java.util.Objects::isNull)) {
            errors.add("'allowedKinds' must not contain blanks");
        }
    }

    /**
     * The authored content only — neither the channel, nor the version and timestamps every save moves — as stable
     * canonical JSON, to tell whether a draft differs from its released row or from what a starter would bring.
     */
    public String fingerprint() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", name);
        content.put("description", description);
        content.put("kind", kind);
        content.put("visibility", visibility);
        content.put("readRoles", getReadRoles());
        content.put("bookRoles", getBookRoles());
        content.put("manageRoles", getManageRoles());
        content.put("publicDetail", publicDetail);
        content.put("allowedKinds", effectiveKinds());
        content.put("capacity", capacity);
        content.put("slotMinutes", slotMinutes);
        content.put("minDurationMinutes", minDurationMinutes);
        content.put("maxDurationMinutes", maxDurationMinutes);
        content.put("horizonDays", horizonDays);
        return CanonicalJson.of(content, Set.of());
    }

    /** A new released row with the draft's content: what a definition's first release creates. */
    public static CalendarDefinition releasedCopyOf(CalendarDefinition draft) {
        CalendarDefinition released = CalendarDefinition.builder()
                .orgKey(draft.orgKey).definitionKey(draft.definitionKey).channel(Channel.RELEASED).build();
        released.replaceWith(draft);
        return released;
    }

    /** Copies everything an author edits from {@code source}, leaving the key, version and timestamps. */
    public void replaceWith(CalendarDefinition source) {
        this.name = source.name;
        this.description = source.description;
        this.kind = source.kind;
        this.visibility = source.visibility;
        this.readRoles = new ArrayList<>(source.getReadRoles());
        this.bookRoles = new ArrayList<>(source.getBookRoles());
        this.manageRoles = new ArrayList<>(source.getManageRoles());
        this.publicDetail = source.publicDetail;
        this.allowedKinds = new ArrayList<>(new LinkedHashSet<>(source.getAllowedKinds()));
        this.capacity = source.capacity;
        this.slotMinutes = source.slotMinutes;
        this.minDurationMinutes = source.minDurationMinutes;
        this.maxDurationMinutes = source.maxDurationMinutes;
        this.horizonDays = source.horizonDays;
    }

    private static boolean isNotPositive(Integer value) {
        return value != null && value < 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
