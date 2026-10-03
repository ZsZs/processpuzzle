package com.processpuzzle.store.usecases.outbound;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface FileStorageService {
    void createBucket(String bucketName);
    void deleteBucket(String bucketName);
    boolean bucketExists(String bucketName);
    List<String> listBuckets();

    void uploadObject(String bucketName, String objectName, InputStream inputStream, String contentType, Map<String, String> metadata);
    StoredObject getObject(String bucketName, String objectName);
    String getObjectUri(String bucketName, String objectName);

    /**
     * A presigned PUT URL, signed for the public endpoint, through which a client uploads straight to
     * storage. The upload must carry {@code Content-Type: contentType}, which the signature covers.
     */
    String getUploadUri(String bucketName, String objectName, String contentType, Duration expiry);

    /**
     * A presigned GET URL signed for the <em>internal</em> endpoint, for a service on the infrastructure
     * network rather than a browser. The host is part of the signature, so a URL signed for the public
     * endpoint is refused when fetched by its internal name, and the reverse.
     */
    String getInternalObjectUri(String bucketName, String objectName, Duration expiry);
    boolean objectExists(String bucketName, String objectName);
    void deleteObject(String bucketName, String objectName);
}
