package com.processpuzzle.starter.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StarterCatalogTest {

    @Test
    void defaultsMissingCollectionsToEmpty() {
        StarterCatalog catalog = new StarterCatalog(1, null);
        StarterCatalog.Starter starter = starter("inventory", null, null);

        assertThat(catalog.starters()).isEmpty();
        assertThat(catalog.starter("missing")).isEmpty();
        assertThat(starter.tags()).isEmpty();
        assertThat(starter.versions()).isEmpty();
        assertThat(starter.version("1.0.0")).isEmpty();
    }

    @Test
    void findsStartersAndVersionsSkippingEntriesWithoutIdentifiers() {
        StarterCatalog.Version version = version("1.0.0", null);
        StarterCatalog.Starter starter = starter("inventory", List.of("stock"), List.of(version(null, null), version));
        StarterCatalog catalog = new StarterCatalog(1, List.of(starter(null, null, null), starter));

        assertThat(catalog.starter("inventory")).contains(starter);
        assertThat(catalog.starter("missing")).isEmpty();
        assertThat(starter.version("1.0.0")).contains(version);
        assertThat(starter.version("2.0.0")).isEmpty();
    }

    @Test
    void snapshotsCollectionsRatherThanRetainingMutableInputs() {
        List<String> tags = new ArrayList<>(List.of("stock"));
        StarterCatalog.Version version = version("1.0.0", null);
        List<StarterCatalog.Version> versions = new ArrayList<>(List.of(version));
        StarterCatalog.Starter starter = starter("inventory", tags, versions);
        List<StarterCatalog.Starter> starters = new ArrayList<>(List.of(starter));
        StarterCatalog catalog = new StarterCatalog(1, starters);
        tags.clear();
        versions.clear();
        starters.clear();

        assertThat(catalog.starters()).containsExactly(starter);
        assertThat(starter.tags()).containsExactly("stock");
        assertThat(starter.versions()).containsExactly(version);
        List<String> catalogTags = starter.tags();
        assertThatThrownBy(() -> catalogTags.add("changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void treatsMissingOrBlankStatusAsPublished(String status) {
        assertThat(version("1.0.0", status).effectiveStatus()).isEqualTo(StarterCatalog.Version.PUBLISHED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"published", "deprecated", "yanked"})
    void retainsExplicitStatuses(String status) {
        assertThat(version("1.0.0", status).effectiveStatus()).isEqualTo(status);
    }

    private static StarterCatalog.Starter starter(String id, List<String> tags, List<StarterCatalog.Version> versions) {
        return new StarterCatalog.Starter(id, null, null, null, null, null, tags, null, versions);
    }

    private static StarterCatalog.Version version(String version, String status) {
        return new StarterCatalog.Version(version, null, status, null, null, null);
    }
}
