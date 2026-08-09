package com.processpuzzle.artifact.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseArtifactTest {

    @Test
    void constructor_shouldDefaultContentTypeToBinary() {
        BaseArtifact artifact = new BaseArtifact("invoice.pdf");

        assertEquals("invoice.pdf", artifact.getName());
        assertEquals(BaseArtifact.DEFAULT_CONTENT_TYPE, artifact.getContentType());
        assertTrue(artifact.isBinary());
    }

    @Test
    void describe_shouldAppendDeclaredContentType() {
        BaseArtifact artifact = new BaseArtifact("invoice.pdf", "application/pdf");

        assertFalse(artifact.isBinary());
        assertEquals("invoice.pdf (application/pdf)", artifact.describe());
    }

    @Test
    void describe_shouldReturnOnlyNameForBinaryArtifacts() {
        BaseArtifact artifact = new BaseArtifact("scan.bin");

        assertEquals("scan.bin", artifact.describe());
    }
}
