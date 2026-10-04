package com.processpuzzle.ai.usecase.port;

import java.time.Duration;

/**
 * Outbound port to object storage, for the photos, videos and crops of this module. Object names are
 * this module's own ({@code <orgKey>/uploads/<mediaKey>}, {@code <orgKey>/crops/<photoId>.jpg}); the
 * adapter decides the bucket.
 *
 * <p>The adapter lives in the composition root, over processpuzzle-store, so that this library does
 * not compile against another module. There is no permissive default — without storage there is
 * nothing to enroll — so with no adapter wired every call throws
 * {@link MediaStoreUnavailableException}, answered as 503.
 */
public interface MediaStore {

    /** Presigned PUT URL for a client; the upload must carry {@code Content-Type: contentType}. */
    String uploadUrl(String objectName, String contentType, Duration expiry);

    /** Presigned GET URL for a browser. */
    String readUrl(String objectName, Duration expiry);

    /** Presigned GET URL signed for the infrastructure network, for the vision server. */
    String internalReadUrl(String objectName, Duration expiry);

    boolean exists(String objectName);

    void put(String objectName, byte[] data, String contentType);

    /** Deletes the object; an absent one is not an error. */
    void delete(String objectName);
}
