package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload slots: the client asks for one, PUTs its file straight to storage, then hands the media key
 * to the call that uses it. Frames never pass through this backend.
 */
@Service
public class MediaUploads {

    private static final Logger LOG = LoggerFactory.getLogger(MediaUploads.class);

    private static final Map<MediaPurpose, Set<String>> CONTENT_TYPES = Map.of(
            MediaPurpose.RECOGNITION_FRAME, Set.of("image/jpeg", "image/png", "image/webp"));

    private final MediaUploadRepository uploads;
    private final MediaStores stores;
    private final AiProperties.Media settings;
    private final OrganizationGuard guard;

    public MediaUploads(MediaUploadRepository uploads, MediaStores stores, AiProperties properties, OrganizationGuard guard) {
        this.uploads = uploads;
        this.stores = stores;
        this.settings = properties.getMedia();
        this.guard = guard;
    }

    @Transactional
    public Slot create(String orgKey, MediaPurpose purpose, String contentType, long sizeBytes) {
        guard.requireAccess(orgKey);
        if (purpose == null) {
            throw AiRequestException.invalid("ai.media.invalid", "purpose is required.");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!CONTENT_TYPES.get(purpose).contains(type)) {
            throw AiRequestException.invalid("ai.media.content-type-unsupported",
                    "'" + contentType + "' is not accepted for " + purpose + "; use one of " + CONTENT_TYPES.get(purpose) + ".");
        }
        long limit = settings.getMaxFrameBytes();
        if (sizeBytes <= 0) {
            throw AiRequestException.invalid("ai.media.invalid", "sizeBytes must be positive.");
        }
        if (sizeBytes > limit) {
            throw new AiRequestException(AiRequestException.Kind.TOO_LARGE, "ai.media.too-large",
                    "The file is " + sizeBytes + " bytes; at most " + limit + " are accepted for " + purpose + ".");
        }
        MediaUpload upload = uploads.save(new MediaUpload(orgKey, purpose, type, sizeBytes,
                Instant.now().plus(settings.getUploadExpiry())));
        String url = stores.get().uploadUrl(upload.getObjectName(), type, settings.getUploadExpiry());
        return new Slot(upload, url, Map.of("Content-Type", type));
    }

    /**
     * Takes the slot for its one use, from within the caller's transaction: it must be this
     * organization's, of the expected purpose, unexpired, unused, and its object must have been uploaded.
     */
    @Transactional
    public MediaUpload claim(String orgKey, String mediaKey, MediaPurpose purpose) {
        MediaUpload upload = parse(mediaKey)
                .flatMap(key -> uploads.findByOrgKeyAndMediaKey(orgKey, key))
                .orElseThrow(() -> AiRequestException.invalid("ai.media.unknown", "Unknown media key '" + mediaKey + "'."));
        Instant now = Instant.now();
        if (upload.getPurpose() != purpose) {
            throw AiRequestException.invalid("ai.media.wrong-purpose",
                    "Media key '" + mediaKey + "' was reserved for " + upload.getPurpose() + ", not " + purpose + ".");
        }
        if (upload.isExpired(now)) {
            throw AiRequestException.invalid("ai.media.expired", "The upload slot of media key '" + mediaKey + "' has expired.");
        }
        if (!stores.get().exists(upload.getObjectName())) {
            throw AiRequestException.invalid("ai.media.not-uploaded", "The upload for media key '" + mediaKey + "' has not completed.");
        }
        if (upload.isUsed()) {
            throw AiRequestException.invalid("ai.media.already-used", "Media key '" + mediaKey + "' has already been used.");
        }
        upload.markUsed(now);
        return upload;
    }

    /** Drops slots that were never used, with whatever was uploaded to them. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        for (MediaUpload upload : uploads.findByUsedAtIsNullAndExpiresAtBefore(Instant.now())) {
            try {
                stores.get().delete(upload.getObjectName());
                uploads.delete(upload);
            } catch (RuntimeException e) {
                LOG.warn("could not discard expired upload {}: {}", upload.getMediaKey(), e.getMessage());
            }
        }
    }

    private static java.util.Optional<UUID> parse(String mediaKey) {
        try {
            return java.util.Optional.of(UUID.fromString(mediaKey));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }

    public record Slot(MediaUpload upload, String uploadUrl, Map<String, String> requiredHeaders) {
    }
}
