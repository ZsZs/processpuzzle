package com.processpuzzle.document.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseDocumentTest {

    @Test
    void constructor_shouldDefaultContentTypeToBinary() {
        BaseDocument document = new BaseDocument("invoice.pdf");

        assertEquals("invoice.pdf", document.getName());
        assertEquals(BaseDocument.DEFAULT_CONTENT_TYPE, document.getContentType());
        assertTrue(document.isBinary());
    }

    @Test
    void describe_shouldAppendDeclaredContentType() {
        BaseDocument document = new BaseDocument("invoice.pdf", "application/pdf");

        assertFalse(document.isBinary());
        assertEquals("invoice.pdf (application/pdf)", document.describe());
    }

    @Test
    void describe_shouldReturnOnlyNameForBinaryDocuments() {
        BaseDocument document = new BaseDocument("scan.bin");

        assertEquals("scan.bin", document.describe());
    }
}
