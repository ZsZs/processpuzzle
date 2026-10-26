package com.processpuzzle.app.usecase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The on-the-wire form of a module's translations: a zip with one {@code <locale>.json} per language at its
 * root, or a single such file.
 *
 * <p>Files are written flat ({@code "route.orders": "Orders"}) and read flat or nested — a translator's
 * tool may well re-nest them, and transloco reads both. Reading is capped the way the starter-bundle reader
 * caps its archives, so a zip bomb cannot be uploaded here either.
 */
public final class ModuleTranslationFiles {

    /** BCP-47 language tag: {@code en}, {@code pt-BR}, {@code zh-Hant}. */
    public static final Pattern LOCALE = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*");

    static final int MAX_ENTRIES = 64;
    static final int MAX_FILE_BYTES = 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;

    private static final String SUFFIX = ".json";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ModuleTranslationFiles() {
    }

    /** A zip of one pretty-printed {@code <locale>.json} per language. */
    public static byte[] zip(Map<String, Map<String, String>> byLocale) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, Map<String, String>> file : byLocale.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey() + SUFFIX));
                zip.write(JSON.writeValueAsBytes(file.getValue()));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * The files of an upload, keyed by the language their name states. A zip is told from a lone file by
     * its signature rather than by its name, which a user is free to get wrong.
     */
    public static Map<String, Map<String, String>> read(String filename, InputStream input) {
        byte[] content = readCapped(input, MAX_TOTAL_BYTES, "The upload");
        Map<String, Map<String, String>> files = new LinkedHashMap<>();
        if (isZip(content)) {
            readZip(content, files);
        } else {
            put(files, filename == null ? "" : filename, content);
        }
        if (files.isEmpty()) {
            throw new ModuleTranslationRejectedException("The upload holds no <locale>.json file.");
        }
        return files;
    }

    private static boolean isZip(byte[] content) {
        return content.length > 3 && content[0] == 'P' && content[1] == 'K';
    }

    private static void readZip(byte[] content, Map<String, Map<String, String>> files) {
        int count = 0;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new ModuleTranslationRejectedException("The archive has more than " + MAX_ENTRIES + " entries.");
                }
                String name = entry.getName();
                if (isArchiverJunk(name)) {
                    continue;
                }
                byte[] file = readCapped(zip, MAX_FILE_BYTES, "'" + name + "'");
                total += file.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new ModuleTranslationRejectedException("The archive uncompresses to more than "
                            + MAX_TOTAL_BYTES + " bytes.");
                }
                put(files, name, file);
            }
        } catch (ZipException e) {
            throw new ModuleTranslationRejectedException("Not a readable zip: " + e.getMessage());
        } catch (IOException e) {
            throw new ModuleTranslationRejectedException("Unable to read the archive: " + e.getMessage());
        }
    }

    /** What Finder and Explorer add to an archive: not the user's files, and not worth failing an upload over. */
    private static boolean isArchiverJunk(String name) {
        return name.startsWith("__MACOSX/") || name.endsWith(".DS_Store") || name.endsWith("/");
    }

    private static void put(Map<String, Map<String, String>> files, String name, byte[] content) {
        String locale = name.endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : "";
        if (!LOCALE.matcher(locale).matches()) {
            throw new ModuleTranslationRejectedException("'" + name + "' is not a <locale>.json file at the root.");
        }
        if (files.put(locale, parse(name, content)) != null) {
            throw new ModuleTranslationRejectedException("The upload holds the language '" + locale + "' twice.");
        }
    }

    private static Map<String, String> parse(String name, byte[] content) {
        JsonNode root;
        try {
            root = JSON.readTree(content);
        } catch (JsonProcessingException e) {
            throw new ModuleTranslationRejectedException("'" + name + "' is not valid JSON: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        if (root == null || !root.isObject()) {
            throw new ModuleTranslationRejectedException("'" + name + "' must hold a JSON object.");
        }
        Map<String, String> flat = new LinkedHashMap<>();
        flatten(name, "", root, flat);
        return flat;
    }

    private static void flatten(String file, String prefix, JsonNode node, Map<String, String> flat) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = prefix + field.getKey();
            JsonNode value = field.getValue();
            if (value.isObject()) {
                flatten(file, key + ".", value, flat);
            } else if (value.isTextual()) {
                flat.put(key, value.asText());
            } else {
                throw new ModuleTranslationRejectedException("'" + file + "': the value of '" + key + "' is not text.");
            }
        }
    }

    private static byte[] readCapped(InputStream input, int limit, String what) {
        try {
            byte[] content = input.readNBytes(limit + 1);
            if (content.length > limit) {
                throw new ModuleTranslationRejectedException(what + " is larger than " + limit + " bytes.");
            }
            return content;
        } catch (IOException e) {
            throw new ModuleTranslationRejectedException("Unable to read " + what + ": " + e.getMessage());
        }
    }
}
