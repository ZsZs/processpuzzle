package com.processpuzzle.starter.bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds bundle zips in memory for the specs. */
public final class TestBundles {

    public static final String MANIFEST = """
            manifestVersion: 1
            id: inventory
            name: Inventory
            version: 1.0.0
            definitionSchemaVersion: 1
            contents:
              entities:
                - entities/item.yaml
              rules:
                - rules/item-rules.yaml
            """;

    private TestBundles() {
    }

    /** The {@link #MANIFEST} bundle with placeholder file contents. */
    public static Map<String, String> inventory() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.yaml", MANIFEST);
        files.put("entities/item.yaml", "entityDefinitions: []\n");
        files.put("rules/item-rules.yaml", "rules: []\n");
        return files;
    }

    public static byte[] zip(Map<String, String> files) {
        Map<String, byte[]> binary = new LinkedHashMap<>();
        files.forEach((name, content) -> binary.put(name, content.getBytes(StandardCharsets.UTF_8)));
        return zipBytes(binary);
    }

    public static byte[] zipBytes(Map<String, byte[]> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
