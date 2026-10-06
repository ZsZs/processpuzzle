package com.processpuzzle.starter;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the starter importer, under {@code base-starter}. Every bundle is untrusted input, so
 * the limits apply to what the zip <em>decompresses</em> to, not to the download's size.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "base-starter")
public class StarterProperties {

    private Bundle bundle = new Bundle();
    private Registry registry = new Registry();

    @Getter
    @Setter
    public static class Bundle {
        /** Most entries, directories included, a bundle zip may hold. */
        private int maxEntries = 200;
        /** Largest uncompressed size of any single file in the bundle. */
        private long maxFileBytes = 1024L * 1024;
        /** Largest uncompressed size of all files of the bundle together. */
        private long maxTotalBytes = 10L * 1024 * 1024;
    }

    /**
     * Where the Business Starter registry is: the public-read {@code processpuzzle-starters} bucket,
     * read over plain HTTP. See business-starters-design.md §5.
     */
    @Getter
    @Setter
    public static class Registry {
        /** The bucket's URL, {@code catalog.json} and the bundle paths resolve against it. Blank: no catalog. */
        private String baseUrl = "";
        /** How long a fetched {@code catalog.json} is reused before it is fetched again. */
        private Duration catalogTtl = Duration.ofMinutes(1);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(30);
        /** Largest {@code catalog.json}, and largest compressed bundle, the backend downloads. */
        private long maxDownloadBytes = 10L * 1024 * 1024;
    }
}
