package com.processpuzzle.store.adapters.outbound;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioPropertiesTest {

    @Test
    void shouldExposeConfiguredValues() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");
        properties.setBuckets(Map.of("image/png", "images"));
        properties.setMimeTypes(Map.of("png", "image/png"));

        assertEquals("http://localhost:9000", properties.getEndpoint());
        assertEquals("access-key", properties.getAccessKey());
        assertEquals("secret-key", properties.getSecretKey());
        assertEquals("images", properties.getBuckets().get("image/png"));
        assertEquals("image/png", properties.getMimeTypes().get("png"));
    }

    @Test
    void shouldProvideThumbnailAndTimeoutDefaults() {
        MinioProperties properties = new MinioProperties();

        MinioProperties.Thumbnail thumbnail = properties.getThumbnail();
        assertTrue(thumbnail.isEnabled());
        assertEquals(200, thumbnail.getMaxDimension());
        assertEquals(0.85, thumbnail.getQuality());

        MinioProperties.HttpTimeouts timeouts = properties.getHttpTimeouts();
        assertEquals(Duration.ofSeconds(5), timeouts.getConnect());
        assertEquals(Duration.ofSeconds(30), timeouts.getRead());
        assertEquals(Duration.ofSeconds(60), timeouts.getWrite());
    }

    @Test
    void shouldAllowOverridingNestedConfiguration() {
        MinioProperties properties = new MinioProperties();

        MinioProperties.Thumbnail thumbnail = new MinioProperties.Thumbnail();
        thumbnail.setEnabled(false);
        thumbnail.setMaxDimension(64);
        thumbnail.setQuality(0.5);
        properties.setThumbnail(thumbnail);

        MinioProperties.HttpTimeouts timeouts = new MinioProperties.HttpTimeouts();
        timeouts.setConnect(Duration.ofSeconds(1));
        timeouts.setRead(Duration.ofSeconds(2));
        timeouts.setWrite(Duration.ofSeconds(3));
        properties.setHttpTimeouts(timeouts);

        assertFalse(properties.getThumbnail().isEnabled());
        assertEquals(64, properties.getThumbnail().getMaxDimension());
        assertEquals(0.5, properties.getThumbnail().getQuality());
        assertEquals(Duration.ofSeconds(1), properties.getHttpTimeouts().getConnect());
        assertEquals(Duration.ofSeconds(2), properties.getHttpTimeouts().getRead());
        assertEquals(Duration.ofSeconds(3), properties.getHttpTimeouts().getWrite());
    }
}
