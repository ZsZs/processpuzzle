package com.processpuzzle.starter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the starter importer, under {@code base-starter}. Every uploaded bundle is untrusted
 * input, so the limits apply to what the zip <em>decompresses</em> to, not to the upload's size.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "base-starter")
public class StarterProperties {

    private Bundle bundle = new Bundle();

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
}
