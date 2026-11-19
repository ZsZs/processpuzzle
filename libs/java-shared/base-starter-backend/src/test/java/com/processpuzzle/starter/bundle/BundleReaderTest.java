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
    void readsAManifestWithoutDefinitionContentsAsAnEmptyBundle() {
        StarterBundle bundle = read(Map.of("manifest.yaml",
                "id: inventory\nversion: 1.0.0\ndefinitionSchemaVersion: 1\n"));

        assertThat(bundle.manifest().contents()).isEmpty();
        assertThat(bundle.files()).isEmpty();
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

    /** The same packager, on a starter whose modules and app bring translations and files along. */
    @Test
    void readsABundleThePackagerBuiltWithAssetsAndTranslations() throws IOException {
        try (InputStream packaged = getClass().getResourceAsStream("/bundles/packaged-race-pages-1.0.0.zip")) {
            StarterBundle bundle = reader.read(packaged);

            assertThat(bundle.manifest().contents()).containsOnlyKeys("modules", "apps", "translations", "assets");
            assertThat(bundle.files()).containsKeys("translations/modules/plan-race/de.json",
                    "assets/apps/app/cards/plan-race.svg", "assets/modules/plan-race/docs/guide.md");
            assertThat(bundle.manifest().integrity().files()).containsKey("assets/apps/app/cards/plan-race.svg");
        }
    }

    @Test
    void readsBinaryAssetsAndTranslationsAndChecksTheirHashes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, (byte) 0xFF};
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.yaml", (TestBundles.MANIFEST
                + "  assets:\n    - assets/apps/app/logo.png\n  translations:\n    - translations/modules/plan/de.json\n"
                + "integrity:\n  algorithm: sha256\n  files:\n    assets/apps/app/logo.png: \"" + BundleReader.sha256(png) + "\"\n")
                .getBytes(StandardCharsets.UTF_8));
        files.put("entities/item.yaml", "entityDefinitions: []\n".getBytes(StandardCharsets.UTF_8));
        files.put("rules/item-rules.yaml", "rules: []\n".getBytes(StandardCharsets.UTF_8));
        files.put("assets/apps/app/logo.png", png);
        files.put("translations/modules/plan/de.json", "{}".getBytes(StandardCharsets.UTF_8));

        StarterBundle bundle = reader.read(new ByteArrayInputStream(TestBundles.zipBytes(files)));

        assertThat(bundle.file("assets/apps/app/logo.png")).isEqualTo(png);
        assertThat(bundle.manifest().contents().get("assets")).containsExactly("assets/apps/app/logo.png");

        files.put("assets/apps/app/logo.png", new byte[] {1, 2, 3});
        assertInvalid(() -> reader.read(new ByteArrayInputStream(TestBundles.zipBytes(files))), "does not match its integrity hash");
    }

    @Test
    void rejectsAnAssetTheManifestDoesNotListOrWhosePathTraverses() {
        Map<String, String> unlisted = TestBundles.inventory();
        unlisted.put("assets/apps/app/logo.png", "x");
        assertInvalid(() -> read(unlisted), "not listed");

        Map<String, String> traversing = TestBundles.inventory();
        traversing.put("manifest.yaml", TestBundles.MANIFEST + "  assets:\n    - assets/apps/../../evil.png\n");
        assertInvalid(() -> read(traversing), "Unsafe path");

        Map<String, String> missing = TestBundles.inventory();
        missing.put("manifest.yaml", TestBundles.MANIFEST + "  translations:\n    - translations/modules/plan/de.json\n");
        assertInvalid(() -> read(missing), "does not contain");
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

    private static Map<String, String> v2(String manifest) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.yaml", manifest);
        files.put("entities/item.yaml", "entityDefinitions: []\n");
        return files;
    }

    private static final String V2_HEAD = "manifestVersion: 2\nname: Inventory\nversion: 1.0.0\ndefinitionSchemaVersion: 1\n";
    private static final String V2_CONTENTS = "contents:\n  entities:\n    - entities/item.yaml\n";

    @Test
    void readsACommonBundle() {
        StarterBundle bundle = read(v2(V2_HEAD + "kind: common\nid: inventory-common\n" + V2_CONTENTS + "migrations: []\n"));

        assertThat(bundle.manifest().isCommon()).isTrue();
        assertThat(bundle.manifest().common()).isNull();
    }

    @Test
    void readsATenantBundleThatNamesItsCommon() {
        StarterBundle bundle = read(v2(V2_HEAD + "id: inventory\ncommon:\n  id: inventory-common\n  minVersion: 1.2.0\n" + V2_CONTENTS));

        assertThat(bundle.manifest().isCommon()).isFalse();
        assertThat(bundle.manifest().common()).isEqualTo(new StarterManifest.Common("inventory-common", "1.2.0"));
    }

    @Test
    void aVersion2ManifestRefusesAMisspeltField() {
        assertInvalid(() -> read(v2(V2_HEAD + "id: inventory\ncommn:\n  id: inventory-common\n" + V2_CONTENTS)), "unknown field 'commn'");
        assertInvalid(() -> read(v2(V2_HEAD + "id: inventory\ncommon:\n  id: inventory-common\n  minVersoin: 1.0.0\n" + V2_CONTENTS)),
                "unknown field 'common.minVersoin'");
    }

    @Test
    void aVersion1ManifestStillIgnoresWhatItDoesNotKnowAndRefusesTheCommonsFields() {
        read(v2("manifestVersion: 1\nid: inventory\nname: Inventory\nversion: 1.0.0\ndefinitionSchemaVersion: 1\nwhatever: 1\n" + V2_CONTENTS));

        assertInvalid(() -> read(v2("manifestVersion: 1\nid: inventory\nname: Inventory\nversion: 1.0.0\ndefinitionSchemaVersion: 1\n"
                + "kind: common\n" + V2_CONTENTS)), "need manifestVersion 2");
    }

    @Test
    void aCommonMustBeNamedAfterItsStarterAndHasNoCommonBlock() {
        assertInvalid(() -> read(v2(V2_HEAD + "kind: common\nid: inventory\n" + V2_CONTENTS)), "'<starter id>-common'");
        assertInvalid(() -> read(v2(V2_HEAD + "kind: common\nid: inventory-common\ncommon:\n  id: x-common\n  minVersion: 1.0.0\n" + V2_CONTENTS)),
                "no 'common' block");
    }

    @Test
    void aTenantMustNameItsOwnCommonAndAMinVersion() {
        assertInvalid(() -> read(v2(V2_HEAD + "id: inventory\ncommon:\n  id: other-common\n  minVersion: 1.0.0\n" + V2_CONTENTS)),
                "'common.id' must be 'inventory-common'");
        assertInvalid(() -> read(v2(V2_HEAD + "id: inventory\ncommon:\n  id: inventory-common\n  minVersion: latest\n" + V2_CONTENTS)),
                "'common.minVersion'");
    }

    @Test
    void aManifestVersionFromTheFutureIsRefused() {
        assertInvalid(() -> read(v2("manifestVersion: 4\nid: inventory\nname: Inventory\nversion: 1.0.0\ndefinitionSchemaVersion: 1\n" + V2_CONTENTS)),
                "Unsupported manifestVersion 4");
    }

    private static final String MIGRATION = "migration:\n  from: 1.0.0\n  to: 1.1.0\n  steps:\n    - kind: entity\n      key: item\n      operation: fillAttribute\n      subject: status\n";

    private static Map<String, String> withMigration(String path, String content) {
        Map<String, String> files = v2(V2_HEAD + "id: inventory\n" + V2_CONTENTS + "migrations:\n  - " + path + "\n");
        files.put(path, content);
        return files;
    }

    @Test
    void readsAMigrationFileThatIsListed() {
        StarterBundle bundle = read(withMigration("migrations/1.0.0-to-1.1.0.yaml", MIGRATION));

        assertThat(bundle.manifest().migrations()).containsExactly("migrations/1.0.0-to-1.1.0.yaml");
        assertThat(bundle.files()).containsKey("migrations/1.0.0-to-1.1.0.yaml");
    }

    @Test
    void aMalformedMigrationFailsTheBundleWithTheFileNamed() {
        assertInvalid(() -> read(withMigration("migrations/1.0.0-to-1.1.0.yaml", MIGRATION.replace("to: 1.1.0", "to: 1.2.0"))), "named for 1.0.0 → 1.1.0");
        assertInvalid(() -> read(withMigration("migrations/upgrade.yaml", MIGRATION)), "migrations/<from>-to-<to>.yaml");
        assertInvalid(() -> read(withMigration("migrations/1.0.0-to-1.1.0.yaml", MIGRATION.replace("operation: fillAttribute", ""))),
                "needs a kind, a key and an operation");
    }

    @Test
    void aMigrationFileTheManifestDoesNotListIsRefused() {
        Map<String, String> files = v2(V2_HEAD + "id: inventory\n" + V2_CONTENTS);
        files.put("migrations/1.0.0-to-1.1.0.yaml", MIGRATION);

        assertInvalid(() -> read(files), "is not listed");
    }

    private static final String V3_HEAD = "manifestVersion: 3\nid: inventory\nname: Inventory\nversion: 1.0.0\n"
            + "definitionSchemaVersion: 1\nkeyPrefix: inv\n";
    private static final String MOUNT = "mounts:\n  - moduleKey: inv-stock\n    basePath: stock\n"
            + "    nav: { icon: inventory, labelKey: nav.title }\n"
            + "    launcher: { image: 'module:launcher.svg', titleKey: launcher.title, contentKeys: [launcher.content], link: overview }\n";

    @Test
    void readsADomainStarterWithItsKeyPrefixAndMounts() {
        StarterManifest manifest = read(v2(V3_HEAD + V2_CONTENTS + MOUNT)).manifest();

        assertThat(manifest.isDomainStarter()).isTrue();
        assertThat(manifest.keyPrefix()).isEqualTo("inv");
        assertThat(manifest.mounts()).singleElement().satisfies(mount -> {
            assertThat(mount.moduleKey()).isEqualTo("inv-stock");
            assertThat(mount.basePath()).isEqualTo("stock");
            assertThat(mount.nav()).isEqualTo(new StarterManifest.Nav("inventory", "nav.title"));
            assertThat(mount.launcher().image()).isEqualTo("module:launcher.svg");
            assertThat(mount.launcher().contentKeys()).containsExactly("launcher.content");
        });
    }

    @Test
    void aDomainStarterNeedsAWellFormedKeyPrefix() {
        assertInvalid(() -> read(v2(V3_HEAD.replace("keyPrefix: inv\n", "") + V2_CONTENTS)), "needs a 'keyPrefix'");
        assertInvalid(() -> read(v2(V3_HEAD.replace("keyPrefix: inv", "keyPrefix: in-v") + V2_CONTENTS)), "needs a 'keyPrefix'");
        assertInvalid(() -> read(v2(V3_HEAD.replace("keyPrefix: inv", "keyPrefix: Inv") + V2_CONTENTS)), "needs a 'keyPrefix'");
    }

    @Test
    void aDomainStarterCarriesNoApplication() {
        Map<String, String> withApp = v2(V3_HEAD + V2_CONTENTS + "  apps:\n    - apps/app.yaml\n");
        withApp.put("apps/app.yaml", "appDefinitions: []\n");
        assertInvalid(() -> read(withApp), "carries no application ('apps')");

        Map<String, String> appText = v2(V3_HEAD + V2_CONTENTS + "  translations:\n    - translations/apps/app/en.json\n");
        appText.put("translations/apps/app/en.json", "{}");
        assertInvalid(() -> read(appText), "belongs to an application");
    }

    @Test
    void aDomainStarterRefusesMalformedMounts() {
        assertInvalid(() -> read(v2(V3_HEAD + V2_CONTENTS + MOUNT.replace("basePath: stock", "basePath: Stock"))), "needs a 'basePath'");
        assertInvalid(() -> read(v2(V3_HEAD + V2_CONTENTS + MOUNT + MOUNT.substring("mounts:\n".length()))), "'inv-stock' is named twice");
        assertInvalid(() -> read(v2(V3_HEAD + V2_CONTENTS + MOUNT.replace("'module:launcher.svg'", "'app:launcher.svg'"))),
                "a launcher image is an asset of the module");
        assertInvalid(() -> read(v2(V3_HEAD + V2_CONTENTS + MOUNT.replace("labelKey: nav.title", "label: Stock"))),
                "unknown field 'mounts[0].nav.label'");
        assertInvalid(() -> read(v2(V3_HEAD.replace("id: inventory", "id: inventory-common\nkind: common") + V2_CONTENTS + MOUNT)),
                "has no 'mounts'");
    }

    @Test
    void keyPrefixAndMountsNeedManifestVersion3() {
        assertInvalid(() -> read(v2(V2_HEAD + "id: inventory\nkeyPrefix: inv\n" + V2_CONTENTS)), "unknown field 'keyPrefix'");
        assertInvalid(() -> read(v2(V3_HEAD.replace("manifestVersion: 3", "manifestVersion: 1") + V2_CONTENTS)),
                "need manifestVersion 3");
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
