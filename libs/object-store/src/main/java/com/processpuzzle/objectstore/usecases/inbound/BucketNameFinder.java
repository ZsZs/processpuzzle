package com.processpuzzle.objectstore.usecases.inbound;

import com.processpuzzle.objectstore.adapters.outbound.MinioProperties;
import org.springframework.stereotype.Service;

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
        return bucketName;
    }
}
