package com.processpuzzle.starter.usecase;

import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import com.processpuzzle.starter.registry.StarterVersions;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The installable part of the registry's catalog. Tenant-free: a starter belongs to no organization.
 *
 * <p>A version is installable when it is not yanked and its {@code definitionSchemaVersion} is one
 * this platform reads; a starter without an installable version is not listed at all. A yanked version
 * stays in the bucket, so organizations that installed it are unaffected — it is only no longer offered.
 */
@Service
public class BrowseCatalog {

    private final StarterRegistry registry;

    public BrowseCatalog(StarterRegistry registry) {
        this.registry = registry;
    }

    /** Sorted by name; each starter's versions newest first. */
    public List<StarterCatalog.Starter> list() {
        return registry.catalog().starters().stream()
                .filter(starter -> starter.id() != null)
                .map(BrowseCatalog::installable)
                .filter(starter -> !starter.versions().isEmpty())
                .sorted(Comparator.comparing(starter -> starter.name() == null ? starter.id() : starter.name()))
                .toList();
    }

    public Optional<StarterCatalog.Starter> get(String starterId) {
        return list().stream().filter(starter -> starter.id().equals(starterId)).findFirst();
    }

    /**
     * @throws StarterNotFoundException if the catalog has no installable {@code version} of the starter
     */
    public StarterCatalog.Version version(String starterId, String version) {
        StarterCatalog.Starter starter = get(starterId)
                .orElseThrow(() -> new StarterNotFoundException("The catalog has no starter '" + starterId + "'."));
        return starter.version(version)
                .orElseThrow(() -> new StarterNotFoundException(
                        "The catalog has no installable version " + version + " of starter '" + starterId + "'."));
    }

    private static StarterCatalog.Starter installable(StarterCatalog.Starter starter) {
        List<StarterCatalog.Version> versions = starter.versions().stream()
                .filter(version -> version.version() != null)
                .filter(version -> !StarterCatalog.Version.YANKED.equals(version.effectiveStatus()))
                .filter(version -> version.definitionSchemaVersion() == null
                        || BundleReader.SUPPORTED_SCHEMA_VERSIONS.contains(version.definitionSchemaVersion()))
                .sorted(Comparator.comparing(StarterCatalog.Version::version, StarterVersions.ASCENDING).reversed())
                .toList();
        return new StarterCatalog.Starter(starter.id(), starter.name(), starter.description(), starter.author(),
                starter.license(), starter.category(), starter.tags(), starter.icon(), versions);
    }
}
