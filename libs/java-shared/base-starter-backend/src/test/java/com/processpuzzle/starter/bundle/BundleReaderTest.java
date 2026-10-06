package com.processpuzzle.starter.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.starter.StarterProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BundleReaderTest {

    private StarterProperties properties;
    private BundleReader reader;

    @BeforeEach
    void setUp() {
        properties = new StarterProperties();
        reader = new BundleReader(properties);
    }

    @Test
    void readsTheManifestAndEveryListedFile() {
        StarterBundle bundle = read(TestBundles.inventory());

        assertThat(bundle.manifest().id()).isEqualTo("inventory");
        assertThat(bundle.manifest().version()).isEqualTo("1.0.0");
        assertThat(bundle.manifest().contents()).containsOnlyKeys("entities", "rules");
        assertThat(new String(bundle.file("entities/item.yaml"), StandardCharsets.UTF_8)).isEqualTo("entityDefinitions: []\n");
        assertThat(bundle.files()).containsOnlyKeys("entities/item.yaml", "rules/item-rules.yaml");
    }

    @Test
    void ignoresCatalogFieldsItDoesNotActOn() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST + "category: inventory\ntags: [stock]\nlocales: [en]\n");

        assertThat(read(files).manifest().id()).isEqualTo("inventory");
    }

    @Test
    void rejectsABundleWithoutAManifest() {
        Map<String, String> files = TestBundles.inventory();
        files.remove("manifest.yaml");

        assertInvalid(() -> read(files), "no manifest.yaml");
    }

    @Test
    void rejectsPathTraversal() {
        Map<String, String> files = TestBundles.inventory();
        files.put("../evil.yaml", "rules: []");

        assertInvalid(() -> read(files), "Unsafe path");
    }

    @Test
    void rejectsAbsolutePathsAndBackslashes() {
        Map<String, String> absolute = TestBundles.inventory();
        absolute.put("/etc/passwd", "x");
        assertInvalid(() -> read(absolute), "Unsafe path");

        Map<String, String> backslash = TestBundles.inventory();
        backslash.put("entities\\..\\evil.yaml", "x");
        assertInvalid(() -> read(backslash), "Unsafe path");
    }

    @Test
    void rejectsAManifestThatListsATraversingPath() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST.replace("rules/item-rules.yaml", "rules/../../item-rules.yaml"));

        assertInvalid(() -> read(files), "Unsafe path");
    }

    @Test
    void rejectsAFileTheManifestDoesNotList() {
        Map<String, String> files = TestBundles.inventory();
        files.put("entities/extra.yaml", "entityDefinitions: []");

        assertInvalid(() -> read(files), "not listed");
    }

    @Test
    void acceptsTheIconWhenTheManifestNamesIt() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST + "icon: icons/box.svg\n");
        files.put("icons/box.svg", "<svg/>");

        assertThat(read(files).files()).containsKey("icons/box.svg");
    }

    /** Built by tools/business-starters/package-starters.mjs, the packager CI runs: what the registry serves. */
    @Test
    void readsABundleThePackagerBuilt() throws IOException {
        try (InputStream packaged = getClass().getResourceAsStream("/bundles/packaged-inventory-1.0.0.zip")) {
            StarterBundle bundle = reader.read(packaged);

            assertThat(bundle.manifest().id()).isEqualTo("inventory");
            assertThat(bundle.manifest().integrity().files()).containsOnlyKeys("entities/item.yaml", "rules/item-rules.yaml");
            assertThat(bundle.files()).containsOnlyKeys("entities/item.yaml", "rules/item-rules.yaml");
        }
    }

    @Test
    void carriesAuthorLicenseAndTheMigrationScript() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST
                + "author: ProcessPuzzle\nlicense: Apache-2.0\nmigration: migrations/1.0.0.yaml\n");
        files.put("migrations/1.0.0.yaml", "steps: []\n");

        StarterBundle bundle = read(files);

        assertThat(bundle.manifest().author()).isEqualTo("ProcessPuzzle");
        assertThat(bundle.manifest().license()).isEqualTo("Apache-2.0");
        assertThat(bundle.files()).containsKey("migrations/1.0.0.yaml");
    }

    @Test
    void rejectsAListedFileThatIsMissing() {
        Map<String, String> files = TestBundles.inventory();
        files.remove("rules/item-rules.yaml");

        assertInvalid(() -> read(files), "does not contain");
    }

    @Test
    void rejectsAnUnsupportedSchemaVersion() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST.replace("definitionSchemaVersion: 1", "definitionSchemaVersion: 2"));

        assertInvalid(() -> read(files), "Unsupported definitionSchemaVersion 2");
    }

    @Test
    void rejectsAManifestWithoutIdOrVersion() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST.replace("version: 1.0.0\n", ""));

        assertInvalid(() -> read(files), "'id' and 'version'");
    }

    @Test
    void rejectsAnUnparseableManifest() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", "contents: [unclosed");

        assertInvalid(() -> read(files), "not valid");
    }

    @Test
    void verifiesIntegrityHashesWhenPresent() {
        Map<String, String> files = TestBundles.inventory();
        String hash = BundleReader.sha256("entityDefinitions: []\n".getBytes(StandardCharsets.UTF_8));
        files.put("manifest.yaml", TestBundles.MANIFEST
                + "integrity:\n  algorithm: sha256\n  files:\n    entities/item.yaml: \"" + hash.toUpperCase() + "\"\n");

        assertThat(read(files).manifest().integrity().files()).containsKey("entities/item.yaml");
    }

    @Test
    void rejectsAFileThatDoesNotMatchItsHash() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST
                + "integrity:\n  algorithm: sha256\n  files:\n    entities/item.yaml: \"00\"\n");

        assertInvalid(() -> read(files), "does not match its integrity hash");
    }

    @Test
    void rejectsAnIntegrityEntryForAMissingFileAndAnUnknownAlgorithm() {
        Map<String, String> missing = TestBundles.inventory();
        missing.put("manifest.yaml", TestBundles.MANIFEST + "integrity:\n  files:\n    entities/other.yaml: \"00\"\n");
        assertInvalid(() -> read(missing), "integrity names");

        Map<String, String> md5 = TestBundles.inventory();
        md5.put("manifest.yaml", TestBundles.MANIFEST + "integrity:\n  algorithm: md5\n  files:\n    entities/item.yaml: \"00\"\n");
        assertInvalid(() -> read(md5), "Unsupported integrity algorithm");
    }

    @Test
    void rejectsTooManyEntries() {
        properties.getBundle().setMaxEntries(2);

        assertTooLarge(() -> read(TestBundles.inventory()), "more than 2 entries");
    }

    @Test
    void rejectsAFileOverThePerFileLimit() {
        properties.getBundle().setMaxFileBytes(10);

        assertTooLarge(() -> read(TestBundles.inventory()), "uncompresses to more than 10 bytes");
    }

    @Test
    void cutsOffAZipBombAtTheTotalLimit() {
        properties.getBundle().setMaxTotalBytes(1024);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.yaml", TestBundles.MANIFEST.getBytes(StandardCharsets.UTF_8));
        // Compresses to a few hundred bytes, inflates to 512 KiB.
        files.put("entities/item.yaml", new byte[512 * 1024]);
        byte[] zip = TestBundles.zipBytes(files);
        assertThat(zip.length).isLessThan(2048);

        assertTooLarge(() -> reader.read(new ByteArrayInputStream(zip)), "more than 1024 bytes");
    }

    @Test
    void rejectsSomethingThatIsNotAZip() {
        assertInvalid(() -> reader.read(new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8))),
                "empty or not a zip");
    }

    private StarterBundle read(Map<String, String> files) {
        return reader.read(new ByteArrayInputStream(TestBundles.zip(files)));
    }

    private static void assertInvalid(ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BundleRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(BundleRejectedException.Reason.INVALID))
                .hasMessageContaining(message);
    }

    private static void assertTooLarge(ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BundleRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(BundleRejectedException.Reason.TOO_LARGE))
                .hasMessageContaining(message);
    }
}
