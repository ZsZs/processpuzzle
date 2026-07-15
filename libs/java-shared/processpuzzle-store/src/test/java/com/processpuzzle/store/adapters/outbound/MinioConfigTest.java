package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinioConfigTest {

    @Test
    void minioClient_shouldBuildClientFromProperties() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");

        MinioConfig config = new MinioConfig(properties);

        MinioClient client = config.minioClient();

        assertNotNull(client);
    }
}
