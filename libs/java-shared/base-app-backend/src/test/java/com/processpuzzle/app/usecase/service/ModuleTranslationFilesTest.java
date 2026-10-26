package com.processpuzzle.app.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ModuleTranslationFilesTest {

    private static byte[] zipOf(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, Map<String, String>> read(String filename, byte[] content) {
        return ModuleTranslationFiles.read(filename, new ByteArrayInputStream(content));
    }

    private static Map<String, Map<String, String>> readText(String filename, String content) {
        return read(filename, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void whatItWritesItReadsBack() {
        Map<String, Map<String, String>> files = new LinkedHashMap<>();
        files.put("en", Map.of("module.name", "Claims"));
        files.put("de", Map.of("module.name", "Schäden"));

        assertThat(read("any.zip", ModuleTranslationFiles.zip(files))).isEqualTo(files);
    }

    @Test
    void aLoneFileTakesItsLanguageFromItsName() {
        assertThat(readText("de.json", "{\"module.name\": \"Schäden\"}"))
                .containsExactly(Map.entry("de", Map.of("module.name", "Schäden")));
    }

    @Test
    void aZipIsToldFromAFileBySignatureNotByName() throws IOException {
        assertThat(read("de.json", zipOf(Map.of("fr.json", "{}")))).containsOnlyKeys("fr");
    }

    @Test
    void nestedJsonIsFlattenedToDottedKeys() {
        assertThat(readText("en.json", "{\"route\": {\"orders\": \"Orders\"}, \"module.name\": \"Claims\"}").get("en"))
                .containsEntry("route.orders", "Orders")
                .containsEntry("module.name", "Claims");
    }

    @Test
    void whatMacOsAndWindowsAddToAnArchiveIsIgnored() throws IOException {
        var files = read("a.zip", zipOf(Map.of("de.json", "{}", "__MACOSX/._de.json", "junk", ".DS_Store", "junk")));

        assertThat(files).containsOnlyKeys("de");
    }

    @Test
    void aValueThatIsNotTextIsRejected() {
        assertThatThrownBy(() -> readText("de.json", "{\"a\": 3}"))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("not text");
    }

    @Test
    void invalidJsonIsRejected() {
        assertThatThrownBy(() -> readText("de.json", "{oops"))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("not valid JSON");
    }

    @Test
    void aFileThatIsNotAnObjectIsRejected() {
        assertThatThrownBy(() -> readText("de.json", "[]"))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("JSON object");
    }

    @Test
    void anEntryOutsideTheArchiveRootIsRejected() throws IOException {
        assertThatThrownBy(() -> read("a.zip", zipOf(Map.of("i18n/de.json", "{}"))))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("at the root");
        assertThatThrownBy(() -> read("a.zip", zipOf(Map.of("../de.json", "{}"))))
                .isInstanceOf(ModuleTranslationRejectedException.class);
    }

    @Test
    void aFileNotNamedAfterALanguageIsRejected() {
        assertThatThrownBy(() -> readText("notes.txt", "{}")).isInstanceOf(ModuleTranslationRejectedException.class);
        assertThatThrownBy(() -> readText(null, "{}")).isInstanceOf(ModuleTranslationRejectedException.class);
    }

    @Test
    void anArchiveWithTooManyEntriesIsRejected() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i <= ModuleTranslationFiles.MAX_ENTRIES; i++) {
            entries.put("" + (char) ('a' + i / 26) + (char) ('a' + i % 26) + ".json", "{}");
        }

        assertThatThrownBy(() -> read("a.zip", zipOf(entries)))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("entries");
    }

    @Test
    void aFileOverTheSizeCapIsRejected() {
        byte[] huge = new byte[ModuleTranslationFiles.MAX_TOTAL_BYTES + 1];

        assertThatThrownBy(() -> read("de.json", huge))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("larger than");
    }

    @Test
    void anEmptyArchiveIsRejected() throws IOException {
        assertThatThrownBy(() -> read("a.zip", zipOf(Map.of())))
                .isInstanceOf(ModuleTranslationRejectedException.class);
    }
}
