package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionRemoval;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.BundleRejectedException;
import com.processpuzzle.starter.bundle.StarterBundle;
import com.processpuzzle.starter.bundle.StarterManifest;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarter;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import com.processpuzzle.starter.domain.OfferedVersion;
import com.processpuzzle.starter.domain.OfferedVersionRepository;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import com.processpuzzle.starter.registry.StarterVersions;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Upgrades an organization to a newer version of the starter it has installed, definition by definition — see
 * {@link UpgradePlanner} for what happens to each. The result is <em>drafts</em>: the definitions are written the way
 * the designer would write them, and the organization's administrator reviews and releases. Unlike a replace-install,
 * an upgrade does not need an empty organization: it keeps what the organization customized, and its data stays.
 *
 * <p>The target is the version asked for, or the next one offered. One version at a time: the target must be the next published version after the installed one, so that a
 * bundle's migrations only ever step from the version before it. A skipped version is a chain of upgrades.
 *
 * <p>Everything is written in one transaction, which a dry run rolls back.
 */
@Service
public class UpgradeStarter {

    private final OrganizationGuard guard;
    private final InstallStarter installer;
    private final StarterRegistry registry;
    private final BundleReader reader;
    private final UpgradePlanner planner;
    private final CommonRules commonRules;
    private final DefinitionProvenanceRepository provenanceRepository;
    private final InstalledStarterRepository installedRepository;
    private final OfferedVersionRepository offered;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public UpgradeStarter(OrganizationGuard guard, InstallStarter installer, StarterRegistry registry, BundleReader reader,
                          UpgradePlanner planner, CommonRules commonRules,
                          DefinitionProvenanceRepository provenanceRepository, InstalledStarterRepository installedRepository,
                          OfferedVersionRepository offered, PlatformTransactionManager transactionManager, ObjectProvider<Clock> clock) {
        this.guard = guard;
        this.installer = installer;
        this.registry = registry;
        this.reader = reader;
        this.planner = planner;
        this.commonRules = commonRules;
        this.provenanceRepository = provenanceRepository;
        this.installedRepository = installedRepository;
        this.offered = offered;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /** An organization's administrator upgrading their own organization. */
    public UpgradeReport execute(String orgKey, String starterId, String version, boolean dryRun, String by) {
        guard.requireDesign(orgKey);
        return upgrade(orgKey, starterId, version, dryRun, by);
    }

    /**
     * An upgrade with no principal's design rights to check, for server-side code that has authorized its caller
     * itself: the rollout of a common's new version.
     */
    public UpgradeReport provision(String orgKey, String starterId, String version, String by) {
        return upgrade(orgKey, starterId, version, false, by);
    }

    private UpgradeReport upgrade(String orgKey, String starterId, String requested, boolean dryRun, String by) {
        InstalledStarter installed = installedRepository.findByOrgKeyAndStarterId(orgKey, starterId).orElse(null);
        if (installed == null) {
            return refused(dryRun, starterId, null, requested, "Starter '" + starterId + "' is not installed in '" + orgKey + "'.");
        }
        String version = requested != null ? requested : nextOffered(starterId, installed.getVersion());
        if (version == null) {
            return refused(dryRun, starterId, installed.getVersion(), null,
                "No newer version of '" + starterId + "' is offered than " + installed.getVersion() + ".");
        }
        String refusal = versionRefusal(starterId, installed.getVersion(), version);
        if (refusal != null) {
            return refused(dryRun, starterId, installed.getVersion(), version, refusal);
        }
        StarterBundle bundle;
        try {
            bundle = reader.read(new ByteArrayInputStream(installer.download(starterId, version, dryRun)));
        } catch (BundleRejectedException e) {
            return UpgradeReport.rejected(dryRun, starterId, installed.getVersion(), version,
                List.of(new ImportReport.Error(e.getFile(), e.getMessage())));
        }
        List<ImportReport.Error> problems = problems(orgKey, starterId, version, bundle.manifest());
        if (!problems.isEmpty()) {
            return UpgradeReport.rejected(dryRun, starterId, installed.getVersion(), version, problems);
        }
        UpgradePlanner.Plan plan = planner.plan(orgKey, bundle);
        return transactionTemplate.execute(status -> apply(orgKey, bundle, plan, installed.getVersion(), dryRun, by, status));
    }

    /**
     * With no version asked for, the next published version after the installed one — as far as the offered version,
     * which an organization never goes past; null when none is offered or the organization is up to date.
     */
    private String nextOffered(String starterId, String installedVersion) {
        String offeredVersion = offered.findById(starterId).map(OfferedVersion::getVersion).orElse(null);
        if (offeredVersion == null || StarterVersions.compare(offeredVersion, installedVersion) <= 0) {
            return null;
        }
        return registry.catalog().starter(starterId).map(StarterCatalog.Starter::versions).orElse(List.of()).stream()
            .filter(each -> each.version() != null && !StarterCatalog.Version.YANKED.equals(each.effectiveStatus()))
            .map(StarterCatalog.Version::version)
            .filter(each -> StarterVersions.compare(each, installedVersion) > 0 && StarterVersions.compare(each, offeredVersion) <= 0)
            .min(StarterVersions.ASCENDING).orElse(null);
    }

    /** The target must be newer than what is installed, and the next published version after it. */
    private String versionRefusal(String starterId, String installedVersion, String target) {
        if (StarterVersions.compare(target, installedVersion) <= 0) {
            return "Version " + installedVersion + " is installed; an upgrade goes to a newer one, not " + target + ".";
        }
        List<String> between = registry.catalog().starter(starterId).map(StarterCatalog.Starter::versions).orElse(List.of()).stream()
            .filter(each -> each.version() != null && !StarterCatalog.Version.YANKED.equals(each.effectiveStatus()))
            .map(StarterCatalog.Version::version)
            .filter(each -> StarterVersions.compare(each, installedVersion) > 0 && StarterVersions.compare(each, target) < 0)
            .sorted(StarterVersions.ASCENDING)
            .toList();
        return between.isEmpty() ? null : "Upgrade to " + between.getFirst() + " first; versions are applied one at a time.";
    }

    private List<ImportReport.Error> problems(String orgKey, String starterId, String version, StarterManifest manifest) {
        List<ImportReport.Error> problems = new ArrayList<>();
        if (!starterId.equals(manifest.id()) || !version.equals(manifest.version())) {
            problems.add(new ImportReport.Error(BundleReader.MANIFEST, "The bundle is starter '" + manifest.id() + "' "
                + manifest.version() + ", not '" + starterId + "' " + version + "."));
        }
        problems.addAll(commonRules.check(orgKey, manifest));
        return problems;
    }

    private UpgradeReport apply(String orgKey, StarterBundle bundle, UpgradePlanner.Plan plan, String from, boolean dryRun,
                                String by, TransactionStatus status) {
        StarterManifest manifest = bundle.manifest();
        List<UpgradeReport.Item> items = new ArrayList<>();
        for (UpgradePlanner.KindPlan kind : plan.kinds()) {
            items.addAll(kind.items());
            List<ImportReport.Error> errors = write(orgKey, bundle, kind);
            if (!errors.isEmpty()) {
                status.setRollbackOnly();
                return UpgradeReport.rejected(dryRun, manifest.id(), from, manifest.version(), errors);
            }
        }
        Map<String, Set<String>> removed = remove(orgKey, plan, items);
        if (dryRun) {
            status.setRollbackOnly();
        } else {
            recordProvenance(orgKey, manifest, plan, removed, by);
        }
        return new UpgradeReport(dryRun, dryRun ? UpgradeReport.Status.WOULD_APPLY : UpgradeReport.Status.APPLIED,
            manifest.id(), from, manifest.version(), items, plan.skippedKinds(), List.of());
    }

    private List<ImportReport.Error> write(String orgKey, StarterBundle bundle, UpgradePlanner.KindPlan kind) {
        List<ImportReport.Error> errors = new ArrayList<>();
        kind.writeByFile().forEach((file, keys) -> {
            if (keys.isEmpty() || !errors.isEmpty()) {
                return;
            }
            ParticipantResult result = kind.participant().applyOnly(orgKey, file, bundle.file(file), keys);
            result.errors().forEach(message -> errors.add(new ImportReport.Error(file, message)));
        });
        return errors;
    }

    /** Dependents first, as an install removes; what a definition still depends on stays, and is reported. */
    private Map<String, Set<String>> remove(String orgKey, UpgradePlanner.Plan plan, List<UpgradeReport.Item> items) {
        Map<String, Set<String>> removed = new java.util.LinkedHashMap<>();
        for (UpgradePlanner.KindPlan kind : plan.kinds().reversed()) {
            if (kind.remove().isEmpty()) {
                continue;
            }
            DefinitionRemoval removal = kind.participant().removeDrafts(orgKey, kind.remove());
            String name = kind.participant().kind();
            removal.removed().forEach(key -> items.add(new UpgradeReport.Item(name, key, UpgradeReport.Action.REMOVE, null)));
            removal.refused().forEach((key, reason) ->
                items.add(new UpgradeReport.Item(name, key, UpgradeReport.Action.KEEP_IN_USE, "no longer in the starter, but " + reason)));
            removed.put(name, Set.copyOf(removal.removed()));
        }
        return removed;
    }

    /**
     * What was written now has this version as its origin, with the hash of what the database holds; what was kept
     * keeps its old row, so it stays "customized" next time; what is gone loses its row.
     */
    private void recordProvenance(String orgKey, StarterManifest manifest, UpgradePlanner.Plan plan,
                                  Map<String, Set<String>> removed, String by) {
        Instant now = clock.instant();
        for (UpgradePlanner.KindPlan kind : plan.kinds()) {
            String name = kind.participant().kind();
            Set<String> written = kind.written();
            Map<String, String> fingerprints = written.isEmpty() ? Map.of() : kind.participant().fingerprints(orgKey, written);
            for (String key : written) {
                String fingerprint = fingerprints.get(key);
                if (fingerprint == null) {
                    throw new IllegalStateException("The " + name + " participant wrote '" + key + "' but reports no fingerprint for it.");
                }
                DefinitionProvenance row = provenanceRepository.findByOrgKeyAndKindAndKey(orgKey, name, key)
                    .orElseGet(() -> new DefinitionProvenance(orgKey, name, key));
                row.recordImport(manifest.id(), manifest.version(), ImportBundle.hash(fingerprint), now, by);
                provenanceRepository.save(row);
            }
            Set<String> gone = new java.util.LinkedHashSet<>(kind.stale());
            gone.addAll(removed.getOrDefault(name, Set.of()));
            gone.forEach(key -> provenanceRepository.findByOrgKeyAndKindAndKey(orgKey, name, key).ifPresent(provenanceRepository::delete));
        }
        installedRepository.findByOrgKeyAndStarterId(orgKey, manifest.id()).ifPresent(installed -> {
            installed.recordInstall(manifest.version(), now, by);
            installedRepository.save(installed);
        });
    }

    private static UpgradeReport refused(boolean dryRun, String starterId, String from, String to, String message) {
        return UpgradeReport.rejected(dryRun, starterId, from, to, List.of(new ImportReport.Error(null, message)));
    }
}
