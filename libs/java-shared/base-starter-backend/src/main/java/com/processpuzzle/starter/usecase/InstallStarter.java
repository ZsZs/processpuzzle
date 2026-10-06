package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Installs a catalog starter into one organization, by reference: the backend fetches the bundle from
 * the registry itself, so the bytes never pass through a client, and refuses a download that does not
 * match the catalog's sha256. The import proper — replacing the organization's definitions — is
 * {@link ImportBundle}'s.
 */
@Service
public class InstallStarter {

    private final OrganizationGuard guard;
    private final BrowseCatalog catalog;
    private final StarterRegistry registry;
    private final ImportBundle importBundle;

    public InstallStarter(OrganizationGuard guard, BrowseCatalog catalog, StarterRegistry registry, ImportBundle importBundle) {
        this.guard = guard;
        this.catalog = catalog;
        this.registry = registry;
        this.importBundle = importBundle;
    }

    /**
     * @throws StarterNotFoundException  if the catalog has no installable such version
     * @throws ImportRejectedException   if the bundle, or the organization's state, is refused
     * @throws com.processpuzzle.starter.registry.RegistryUnavailableException if the registry cannot be read
     */
    public ImportReport execute(String orgKey, String starterId, String version, boolean dryRun, String installedBy) {
        // Checked before the registry is touched, so an unauthorized caller learns nothing from it.
        guard.requireDesign(orgKey);

        StarterCatalog.Version entry = catalog.version(starterId, version);
        byte[] bundle = registry.bundle(entry.bundle());
        String actual = BundleReader.sha256(bundle);
        if (entry.sha256() == null || !actual.equals(entry.sha256().trim().toLowerCase(Locale.ROOT))) {
            throw new ImportRejectedException(ImportReport.rejected(dryRun, starterId, version, List.of(
                    new ImportReport.Error(entry.bundle(), "The downloaded bundle does not match the catalog's sha256."))),
                    ImportRejectedException.Reason.INVALID);
        }
        return importBundle.execute(orgKey, new ByteArrayInputStream(bundle), dryRun, installedBy,
                new ImportBundle.Expected(starterId, version));
    }
}
