package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinioHealthIndicatorTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioHealthIndicator healthIndicator = new MinioHealthIndicator(minioClient);

    @Test
    void reportsUpWhenMinioAcceptsAuthenticatedRequests() throws Exception {
        when(minioClient.listBuckets()).thenReturn(List.of());

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenMinioRejectsAuthenticatedRequests() throws Exception {
        when(minioClient.listBuckets()).thenThrow(new IllegalStateException("Access denied"));

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
