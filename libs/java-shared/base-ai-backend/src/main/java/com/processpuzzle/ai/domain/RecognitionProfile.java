package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How subjects of one entity type are recognized: which attribute holds their reference photos, which
 * object class the detector looks for, which attribute holds the registered identifier, and the
 * matching thresholds. One per
 * ({@code orgKey}, {@code entityName}), addressed by entityName like base-state's state machines.
 */
@Entity
@Table(name = "ai_recognition_profiles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "entity_name"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecognitionProfile {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Setter(AccessLevel.NONE)
    @Column(name = "entity_name", nullable = false, length = 200)
    private String entityName;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "detector_class", nullable = false, length = 100)
    private String detectorClass;

    /**
     * The ARTIFACT attribute holding the subjects' reference photos. Nullable in the schema only because
     * {@code ddl-auto} cannot add a NOT NULL column to a populated table; every write path requires it.
     */
    @Column(name = "gallery_attribute_key", length = 200)
    private String galleryAttributeKey;

    @Column(name = "identifier_attribute_key", length = 200)
    private String identifierAttributeKey;

    @Column(name = "identifier_pattern", length = 500)
    private String identifierPattern;

    @Embedded
    private MatchingSettings matching = MatchingSettings.defaults();

    @Version
    @Setter(AccessLevel.NONE)
    private long version;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RecognitionProfile(String orgKey, String entityName) {
        this.id = UUID.randomUUID();
        this.orgKey = orgKey;
        this.entityName = entityName;
    }

    /** Whether subjects of this profile are matched by identifier text as well as by appearance. */
    public boolean readsIdentifier() {
        return identifierAttributeKey != null && !identifierAttributeKey.isBlank();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
