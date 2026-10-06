package com.processpuzzle.starter.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.starter.StarterProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Reads an uploaded zip into a {@link StarterBundle}, treating it as untrusted input throughout.
 *
 * <p>Nothing is extracted to disk; entries are read into memory under the
 * {@link StarterProperties.Bundle} limits, which bound the <em>uncompressed</em> size, so a zip bomb
 * is cut off at the limit rather than inflated. Entry names are checked against a strict relative-path
 * pattern, which rules out traversal ({@code ../}), absolute paths and backslashes. Every file must be
 * listed by the manifest, every listed file must be present, and when the manifest carries
 * {@code integrity} hashes each one must match.
 */
@Component
public class BundleReader {

    public static final String MANIFEST = "manifest.yaml";
    public static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1);

    private static final Pattern SAFE_PATH =
            Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*");

    private final StarterProperties.Bundle limits;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public BundleReader(StarterProperties properties) {
        this.limits = properties.getBundle();
    }

    public StarterBundle read(InputStream input) {
        Map<String, byte[]> entries = readEntries(input);
        byte[] manifestBytes = entries.remove(MANIFEST);
        if (manifestBytes == null) {
            throw BundleRejectedException.invalid(null, "The bundle has no " + MANIFEST + " at its root.");
        }
        StarterManifest manifest = parseManifest(manifestBytes);
        validateManifest(manifest);
        validateFileList(manifest, entries);
        verifyIntegrity(manifest, entries);
        return new StarterBundle(manifest, entries);
    }

    private Map<String, byte[]> readEntries(InputStream input) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > limits.getMaxEntries()) {
                    throw BundleRejectedException.tooLarge(
                            "The bundle has more than " + limits.getMaxEntries() + " entries.");
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    requireSafePath(name.substring(0, name.length() - 1));
                    continue;
                }
                requireSafePath(name);
                byte[] content = readCapped(zip, name);
                total += content.length;
                if (total > limits.getMaxTotalBytes()) {
                    throw BundleRejectedException.tooLarge(
                            "The bundle uncompresses to more than " + limits.getMaxTotalBytes() + " bytes.");
                }
                if (entries.put(name, content) != null) {
                    throw BundleRejectedException.invalid(name, "The bundle holds '" + name + "' twice.");
                }
            }
        } catch (ZipException e) {
            throw BundleRejectedException.invalid(null, "Not a readable zip: " + e.getMessage());
        } catch (IOException e) {
            throw BundleRejectedException.invalid(null, "Unable to read the bundle: " + e.getMessage());
        }
        if (count == 0) {
            throw BundleRejectedException.invalid(null, "The upload is empty or not a zip.");
        }
        return entries;
    }

    private byte[] readCapped(ZipInputStream zip, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            size += read;
            if (size > limits.getMaxFileBytes()) {
                throw BundleRejectedException.tooLarge(
                        "'" + name + "' uncompresses to more than " + limits.getMaxFileBytes() + " bytes.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private StarterManifest parseManifest(byte[] manifestBytes) {
        StarterManifest manifest;
        try {
            manifest = yamlMapper.readValue(manifestBytes, StarterManifest.class);
        } catch (IOException e) {
            throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " is not valid: " + e.getMessage());
        }
        if (manifest == null) {
            throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " is empty.");
        }
        return manifest;
    }

    private void validateManifest(StarterManifest manifest) {
        if (isBlank(manifest.id()) || isBlank(manifest.version())) {
            throw BundleRejectedException.invalid(MANIFEST, MANIFEST + " must declare 'id' and 'version'.");
        }
        if (manifest.definitionSchemaVersion() == null
                || !SUPPORTED_SCHEMA_VERSIONS.contains(manifest.definitionSchemaVersion())) {
            throw BundleRejectedException.invalid(MANIFEST, "Unsupported definitionSchemaVersion "
                    + manifest.definitionSchemaVersion() + "; supported: " + SUPPORTED_SCHEMA_VERSIONS + ".");
        }
        manifest.contents().values().forEach(paths -> paths.forEach(this::requireSafePath));
    }

    /** Listed means: a contents file, the icon, or the migration script. Nothing else may ride along. */
    private void validateFileList(StarterManifest manifest, Map<String, byte[]> entries) {
        Set<String> contentFiles = new LinkedHashSet<>();
        manifest.contents().values().forEach(contentFiles::addAll);
        for (String path : contentFiles) {
            if (!entries.containsKey(path)) {
                throw BundleRejectedException.invalid(path,
                        MANIFEST + " lists '" + path + "', which the bundle does not contain.");
            }
        }

        Set<String> listed = new LinkedHashSet<>(contentFiles);
        if (manifest.icon() != null) {
            listed.add(manifest.icon());
        }
        if (manifest.migration() != null) {
            listed.add(manifest.migration());
        }
        for (String path : entries.keySet()) {
            if (!listed.contains(path)) {
                throw BundleRejectedException.invalid(path, "'" + path + "' is not listed in " + MANIFEST + ".");
            }
        }
    }

    private void verifyIntegrity(StarterManifest manifest, Map<String, byte[]> entries) {
        StarterManifest.Integrity integrity = manifest.integrity();
        if (integrity == null || integrity.files().isEmpty()) {
            return;
        }
        if (integrity.algorithm() != null && !"sha256".equalsIgnoreCase(integrity.algorithm())) {
            throw BundleRejectedException.invalid(MANIFEST,
                    "Unsupported integrity algorithm '" + integrity.algorithm() + "'.");
        }
        integrity.files().forEach((path, hash) -> {
            byte[] content = entries.get(path);
            if (content == null) {
                throw BundleRejectedException.invalid(path,
                        "integrity names '" + path + "', which the bundle does not contain.");
            }
            if (hash == null || !sha256(content).equals(hash.trim().toLowerCase(Locale.ROOT))) {
                throw BundleRejectedException.invalid(path, "'" + path + "' does not match its integrity hash.");
            }
        });
    }

    private void requireSafePath(String path) {
        if (path == null || !SAFE_PATH.matcher(path).matches() || List.of(path.split("/")).contains("..")) {
            throw BundleRejectedException.invalid(path, "Unsafe path in bundle: '" + path + "'.");
        }
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
