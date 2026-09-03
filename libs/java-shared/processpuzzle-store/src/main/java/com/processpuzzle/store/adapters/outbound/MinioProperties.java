package com.processpuzzle.store.adapters.outbound;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Setter
@Getter
@Component
@PropertySource(value = "classpath:minio-config.yaml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String publicEndpoint;
    /**
     * Prepended to every bucket name this application chooses, as {@code <prefix>-<purpose>} — so
     * {@code processpuzzle-admin} yields {@code processpuzzle-admin-documents}. One MinIO serves every
     * application stack, and the prefix is the whole isolation between them; see
     * docs/application-stacks.md.
     *
     * <p>Empty by default, which keeps the flat bucket names an existing deployment already holds
     * objects in. {@link com.processpuzzle.store.usecases.inbound.BucketNameFinder} is the only place
     * that applies it, because it is the only place a bucket name is chosen rather than echoed back.
     */
    private String bucketPrefix = "";
    private Map<String, String> buckets;
    private Map<String, String> mimeTypes;
    private Thumbnail thumbnail = new Thumbnail();
    private HttpTimeouts httpTimeouts = new HttpTimeouts();

    @Setter
    @Getter
    public static class Thumbnail {
        private boolean enabled = true;
        private int maxDimension = 200;
        private double quality = 0.85;
    }

    @Setter
    @Getter
    public static class HttpTimeouts {
        private Duration connect = Duration.ofSeconds(5);
        private Duration read = Duration.ofSeconds(30);
        private Duration write = Duration.ofSeconds(60);
    }
}
