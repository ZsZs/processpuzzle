package com.processpuzzle.store.adapters.outbound;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Bean
    @Qualifier("minioPresignClient")
    public MinioClient minioPresignClient() {
        String endpoint = minioProperties.getPublicEndpoint() != null
                ? minioProperties.getPublicEndpoint()
                : minioProperties.getEndpoint();
        return MinioClient.builder()
                .endpoint(endpoint)
                .region("us-east-1")           // avoids a getBucketLocation network call
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
