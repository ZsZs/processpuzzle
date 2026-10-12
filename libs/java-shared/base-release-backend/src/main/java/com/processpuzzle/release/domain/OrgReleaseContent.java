package com.processpuzzle.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * What one {@link OrgReleaseItem} released: the definition's canonical JSON, as {@code CanonicalJson} writes it.
 *
 * <p>Kept so that what an organization ran at any release can be read back — for a diff between releases, and for
 * returning to one or binding running work to it later. A row of its own rather than a column of the item, because
 * the release history lists items and would otherwise read every definition's content to show their names.
 * Removals have none.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "org_release_contents")
public class OrgReleaseContent {

    /** The id of the {@link OrgReleaseItem} this is the content of. */
    @Id
    @Column(name = "item_id", updatable = false)
    private UUID itemId;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    public OrgReleaseContent(UUID itemId, String orgKey, String content) {
        this.itemId = itemId;
        this.orgKey = orgKey;
        this.content = content;
    }
}
