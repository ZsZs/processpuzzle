package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.adapters.outbound.MinioProperties;
import org.springframework.stereotype.Service;

/**
 * Chooses the bucket an object belongs in, from its mime type.
 *
 * <p>The only place in the application where a bucket name is <em>decided</em>: {@code ObjectEndpoint}
 * echoes back names the server handed out earlier, and {@code UploadObject} asks {@code CreateBucket}
 * to make whatever this returns. That is why the per-stack prefix is applied here and nowhere else —
 * see {@link MinioProperties#getBucketPrefix()} and docs/application-stacks.md.
 */
@Service
public class BucketNameFinder {
    private final MinioProperties minioProperties;

    public BucketNameFinder(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    public String findBucketName(String mimeType) {
        String normalizedMimeType = mimeType.replaceAll("[^a-zA-Z0-9]", "");
        String bucketName = minioProperties.getMimeTypes().get(normalizedMimeType);
        if (bucketName == null) {
            bucketName = minioProperties.getBuckets().get("documents");
        }
        return prefixed(bucketName);
    }

    private String prefixed(String bucketName) {
        String prefix = minioProperties.getBucketPrefix();
        if (bucketName == null || prefix == null || prefix.isBlank()) {
            return bucketName;
        }
        return prefix + "-" + bucketName;
    }
}
