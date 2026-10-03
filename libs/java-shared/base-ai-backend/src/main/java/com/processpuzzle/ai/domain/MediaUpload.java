package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An upload slot handed to a client: the object it may PUT, and until when. The {@code mediaKey} is
 * the slot's id, which the client passes back to the enrollment or job call. A slot is used at most
 * once; an unused one is discarded with its object after {@code expiresAt}.
 */
@Entity
@Table(name = "ai_media_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaUpload {

    @Id
    @Column(name = "media_key")
    private UUID mediaKey;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaPurpose purpose;

    @Column(name = "object_name", nullable = false, length = 300)
    private String objectName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public MediaUpload(String orgKey, MediaPurpose purpose, String contentType, long sizeBytes, Instant expiresAt) {
        this.mediaKey = UUID.randomUUID();
        this.orgKey = orgKey;
        this.purpose = purpose;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.expiresAt = expiresAt;
        this.objectName = orgKey + "/uploads/" + mediaKey;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !isUsed() && now.isAfter(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
