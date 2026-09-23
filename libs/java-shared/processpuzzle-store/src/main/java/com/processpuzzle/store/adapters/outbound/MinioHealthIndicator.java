package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Verifies that the configured MinIO client can complete an authenticated S3 request.
 */
@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {
    private final MinioClient minioClient;

    public MinioHealthIndicator(@Qualifier("minioClient") MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        try {
            minioClient.listBuckets();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
