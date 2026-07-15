package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
    private final MinioProperties minioProperties;

    public MinioConfig(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Bean
    public MinioClient minioClient() {
        MinioProperties.HttpTimeouts timeouts = minioProperties.getHttpTimeouts();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeouts.getConnect())
                .readTimeout(timeouts.getRead())
                .writeTimeout(timeouts.getWrite())
                .build();

        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .httpClient(httpClient)
                .build();
    }
}
