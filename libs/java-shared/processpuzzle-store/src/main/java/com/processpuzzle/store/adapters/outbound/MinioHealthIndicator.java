package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies that the configured MinIO client can complete an authenticated S3 request.
 * <p>
 * Logs only on a change of state: the container healthcheck polls every 15s, so logging each failure
 * would bury the one line that explains it.
 */
@Slf4j
@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {
    private final MinioClient minioClient;
    private final AtomicBoolean down = new AtomicBoolean(false);

    public MinioHealthIndicator(@Qualifier("minioClient") MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        try {
            minioClient.listBuckets();
            if (down.compareAndSet(true, false)) {
                log.info("MinIO health check recovered.");
            }
            return Health.up().build();
        } catch (Exception e) {
            if (down.compareAndSet(false, true)) {
                log.warn("MinIO health check failed; the backend reports not ready until it recovers. "
                        + "Check that MINIO_SECRET_KEY matches the MinIO service account password.", e);
            }
            return Health.down(e).build();
        }
    }
}
