package com.processpuzzle.release.domain;

import com.processpuzzle.core.definition.PendingChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One definition a release carried, and the hash of what it released, so that a later release can be
 * told apart from this one without keeping the content.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "org_release_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "release_number", "kind", "definition_key"}))
public class OrgReleaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @Column(name = "release_number", nullable = false, updatable = false)
    private long releaseNumber;

    /** Where the item stands in its release: release order, by kind and then by key. */
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(nullable = false, updatable = false, length = 32)
    private String kind;

    @Column(name = "definition_key", nullable = false, updatable = false)
    private String definitionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 16)
    private PendingChange.Change change;

    /** SHA-256 of the released definition's canonical JSON; null for a removal. */
    @Column(name = "content_hash", updatable = false, length = 64)
    private String contentHash;

    public OrgReleaseItem(String orgKey, long releaseNumber, int sequenceNo, String kind, String definitionKey,
                          PendingChange.Change change, String contentHash) {
        this.orgKey = orgKey;
        this.releaseNumber = releaseNumber;
        this.sequenceNo = sequenceNo;
        this.kind = kind;
        this.definitionKey = definitionKey;
        this.change = change;
        this.contentHash = contentHash;
    }
}
