package com.processpuzzle.store.usecases.inbound;

import com.processpuzzle.store.adapters.outbound.MinioProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which bucket an object lands in — the one decision in the store that a per-stack prefix has to
 * reach, since every other code path echoes a name this class produced earlier.
 */
class BucketNameFinderTest {

    private static MinioProperties properties(String bucketPrefix) {
        MinioProperties properties = new MinioProperties();
        properties.setBucketPrefix(bucketPrefix);
        properties.setBuckets(Map.of("documents", "documents", "images", "images"));
        properties.setMimeTypes(Map.of("imagepng", "images"));
        return properties;
    }

    @Test
    void mapsAMimeTypeToItsBucket() {
        assertThat(new BucketNameFinder(properties("")).findBucketName("image/png"))
                .isEqualTo("images");
    }

    /** An unmapped mime type is a document, which is what makes uploads of anything at all work. */
    @Test
    void fallsBackToTheDocumentsBucket() {
        assertThat(new BucketNameFinder(properties("")).findBucketName("application/x-unknown"))
                .isEqualTo("documents");
    }

    @Test
    void prependsTheStackPrefixWhenOneIsConfigured() {
        BucketNameFinder finder = new BucketNameFinder(properties("processpuzzle-admin"));

        assertThat(finder.findBucketName("image/png")).isEqualTo("processpuzzle-admin-images");
        assertThat(finder.findBucketName("application/x-unknown"))
                .isEqualTo("processpuzzle-admin-documents");
    }

    /**
     * Blank as well as empty, because an unset environment variable arrives as an empty string and a
     * prefix of {@code " "} would silently create buckets named {@code " -documents"}.
     */
    @Test
    void treatsABlankPrefixAsNoPrefix() {
        assertThat(new BucketNameFinder(properties("   ")).findBucketName("image/png"))
                .isEqualTo("images");
        assertThat(new BucketNameFinder(properties(null)).findBucketName("image/png"))
                .isEqualTo("images");
    }
}
