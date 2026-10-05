package com.processpuzzle.starter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Which starter one imported definition came from, and what it looked like right after the import.
 *
 * <p>Owned by the importer, so no feature table has to change to carry provenance. {@code contentHash}
 * is the sha256 of the owning participant's canonical fingerprint taken just after the import wrote
 * the definition; a definition is <em>customized</em> when its current fingerprint no longer hashes
 * to it.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "starter_definition_provenance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "kind", "definition_key"}))
public class DefinitionProvenance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @Column(nullable = false, updatable = false)
    private String kind;

    /** {@code key} is reserved in some SQL dialects, hence the column name. */
    @Column(name = "definition_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private String starterId;

    @Column(nullable = false)
    private String starterVersion;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Instant importedAt;

    private String importedBy;

    public DefinitionProvenance(String orgKey, String kind, String key) {
        this.orgKey = orgKey;
        this.kind = kind;
        this.key = key;
    }

    /** The definition was (re-)imported from {@code starterId} at {@code starterVersion}. */
    public void recordImport(String starterId, String starterVersion, String contentHash,
                             Instant importedAt, String importedBy) {
        this.starterId = starterId;
        this.starterVersion = starterVersion;
        this.contentHash = contentHash;
        this.importedAt = importedAt;
        this.importedBy = importedBy;
    }
}
