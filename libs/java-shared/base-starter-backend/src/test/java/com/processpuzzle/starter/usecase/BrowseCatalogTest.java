package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrowseCatalogTest {

    private static StarterCatalog.Version version(String version, String status, Integer schema) {
        return new StarterCatalog.Version(version, "2026-10-06T00:00:00Z", status, schema, "abc", "x/" + version + "/bundle.zip");
    }

    private static StarterCatalog.Starter starter(String id, String name, StarterCatalog.Version... versions) {
        return new StarterCatalog.Starter(id, name, null, null, null, null, null, null, List.of(versions));
    }

    private static BrowseCatalog browse(StarterCatalog.Starter... starters) {
        StarterCatalog catalog = new StarterCatalog(1, List.of(starters));
        return new BrowseCatalog(new StarterRegistry() {
            @Override
            public StarterCatalog catalog() {
                return catalog;
            }

            @Override
            public byte[] bundle(String path) {
                throw new UnsupportedOperationException();
            }
        });
    }

    @Test
    void listsByNameWithVersionsNewestFirst() {
        BrowseCatalog browse = browse(
                starter("sail", "Sail Race Organizer", version("1.9.0", null, 1), version("1.10.0", "published", 1),
                        version("1.10.0-rc.1", "published", 1)),
                starter("inventory", "Inventory", version("1.0.0", "deprecated", 1)));

        assertThat(browse.list()).extracting(StarterCatalog.Starter::id).containsExactly("inventory", "sail");
        assertThat(browse.get("sail").orElseThrow().versions()).extracting(StarterCatalog.Version::version)
                .containsExactly("1.10.0", "1.10.0-rc.1", "1.9.0");
    }

    @Test
    void leavesOutYankedAndUnreadableVersionsAndStartersLeftWithNone() {
        BrowseCatalog browse = browse(
                starter("sail", "Sail", version("1.0.0", "yanked", 1), version("1.1.0", null, 2), version("1.2.0", null, 1)),
                starter("gone", "Gone", version("1.0.0", "yanked", 1)));

        assertThat(browse.list()).singleElement().satisfies(starter ->
                assertThat(starter.versions()).extracting(StarterCatalog.Version::version).containsExactly("1.2.0"));
        assertThat(browse.get("gone")).isEmpty();
    }

    @Test
    void handlesLegacyNamesAndSchemasButOmitsMissingIdentifiers() {
        BrowseCatalog browse = browse(
                starter(null, "Unnamed", version("1.0.0", null, 1)),
                starter("inventory", null, version(null, null, 1), version("1.0.0", null, null)),
                starter("sail", "Sail", version("1.0.0", null, 1)));

        assertThat(browse.list()).extracting(StarterCatalog.Starter::id).containsExactly("sail", "inventory");
        assertThat(browse.get("inventory").orElseThrow().versions()).extracting(StarterCatalog.Version::version)
                .containsExactly("1.0.0");
    }

    @Test
    void findsAnInstallableVersionOrSaysWhatIsMissing() {
        BrowseCatalog browse = browse(starter("sail", "Sail", version("1.0.0", null, 1), version("0.9.0", "yanked", 1)));

        assertThat(browse.version("sail", "1.0.0").bundle()).isEqualTo("x/1.0.0/bundle.zip");
        assertThatThrownBy(() -> browse.version("sail", "0.9.0"))
                .isInstanceOf(StarterNotFoundException.class).hasMessageContaining("no installable version 0.9.0");
        assertThatThrownBy(() -> browse.version("billing", "1.0.0"))
                .isInstanceOf(StarterNotFoundException.class).hasMessageContaining("no starter 'billing'");
    }
}
