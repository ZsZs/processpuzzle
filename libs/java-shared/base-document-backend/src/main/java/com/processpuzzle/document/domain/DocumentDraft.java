package com.processpuzzle.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The editable content of one locale of one document. This is what an author works on; readers
 * never see it — see {@link PublishedDocument}.
 *
 * <h2>Why {@code revision} is not the {@code @Version}</h2>
 *
 * <p>{@code revision} is a plain counter over content edits, bumped explicitly on every write
 * here, and {@code publishedRevision} on the snapshot is compared against it to derive
 * {@link DocumentStatus}. That comparison is exactly why it cannot be a Hibernate-managed
 * {@code @Version}: publishing sets {@code publishedRevision} equal to {@code revision}, and a
 * managed version increments on any dirty flush, so the counter would move the instant the
 * publish committed and every freshly published translation would immediately report unpublished
 * edits. {@code AppDefinition} documents the same trap for the same reason.
 *
 * <p>Optimistic locking is therefore a separate concern, handled by {@code lockVersion} — which
 * is per translation, so two translators working in different languages never contend, and a
 * Tiptap autosave in one locale cannot fail because someone saved another.
 *
 * <h2>{@code basedOnRevision}</h2>
 *
 * <p>Which revision of the document's source locale this translation was made from. Null for the
 * source locale itself, which is based on nothing. Compared against the source's current
 * {@code revision} it answers "has the original changed since this was translated?", which is the
 * single most useful signal in a multi-language document and costs one column.
 */
@Entity
@Table(name = "document_drafts")
@IdClass(DocumentTranslationKey.class)
public class DocumentDraft {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "document_id", length = 36)
    private String documentId;

    @Id
    @Column(length = 6)
    private String locale;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Convert(converter = DocumentContentConverter.class)
    private DocumentContent content = DocumentContent.empty();

    @Column(nullable = false)
    private Long revision = 1L;

    @Column(name = "based_on_revision")
    private Long basedOnRevision;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DocumentDraft() {
        // required by JPA
    }

    public DocumentDraft(String orgKey, String documentId, String locale, DocumentContent content, Long basedOnRevision) {
        this.orgKey = orgKey;
        this.documentId = documentId;
        this.locale = locale;
        this.content = content == null ? DocumentContent.empty() : content;
        this.basedOnRevision = basedOnRevision;
        this.revision = 1L;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Replaces the whole block list and bumps {@link #getRevision()}. */
    public void replaceBlocks(List<DocumentBlock> newBlocks) {
        this.content = getContent().withBlocks(newBlocks);
        this.revision = this.revision == null ? 1L : this.revision + 1L;
    }

    /**
     * Reverts this draft to a published snapshot, taking that snapshot's revision back as the
     * current one so {@link DocumentStatus} derives to {@code PUBLISHED} again. Deliberately does
     * <em>not</em> bump the counter: discarding a draft returns to a state that was already
     * published rather than creating a new one.
     */
    public void revertTo(DocumentContent publishedContent, Long publishedRevision) {
        this.content = publishedContent == null ? DocumentContent.empty() : publishedContent;
        this.revision = publishedRevision;
    }

    /** Records that this translation now reflects the given revision of the source locale. */
    public void rebaseOn(Long sourceRevision) {
        this.basedOnRevision = sourceRevision;
    }

    /**
     * True when the source locale has moved on since this translation was made. A translation
     * with no recorded base is not reported as stale — the source locale itself is the common
     * case, and guessing about an unknown base would cry wolf on every import.
     */
    public boolean isOutOfDate(Long sourceRevision) {
        return basedOnRevision != null && sourceRevision != null && basedOnRevision < sourceRevision;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getLocale() {
        return locale;
    }

    public DocumentContent getContent() {
        return content == null ? DocumentContent.empty() : content;
    }

    public List<DocumentBlock> getBlocks() {
        return getContent().blocks();
    }

    public Long getRevision() {
        return revision;
    }

    public Long getBasedOnRevision() {
        return basedOnRevision;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
