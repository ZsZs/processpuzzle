package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void logsTheFailureOnceAndTheRecovery(CapturedOutput output) throws Exception {
        when(minioClient.listBuckets())
                .thenThrow(new IllegalStateException("Access denied"))
                .thenThrow(new IllegalStateException("Access denied"))
                .thenReturn(List.of());

        healthIndicator.health();
        healthIndicator.health();
        healthIndicator.health();

        assertThat(output.getOut().split("MinIO health check failed", -1)).hasSize(2);
        assertThat(output.getOut()).contains("Access denied").contains("MinIO health check recovered.");
    }
}
