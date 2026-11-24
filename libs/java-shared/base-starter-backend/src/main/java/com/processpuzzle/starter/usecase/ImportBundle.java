package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.InstanceDataProbe;
import com.processpuzzle.core.definition.ModuleMountPort;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.BundleRejectedException;
import com.processpuzzle.starter.bundle.ContentGroups;
import com.processpuzzle.starter.bundle.StarterBundle;
import com.processpuzzle.starter.bundle.StarterManifest;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarter;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Installs one Business Starter bundle into one organization.
 *
 * <p>A <em>domain starter</em> ({@code manifestVersion: 3}, not a common) is added to the organization: its
 * definitions are written next to what the organization and its other starters hold, nothing is removed, and it is
 * refused (409) when it would overwrite a definition that exists already or when it is installed already — upgrade
 * it then. Nothing existing is touched, so no instance data can be orphaned and none is checked.
 *
 * <p>Any other bundle replaces: it is all or nothing, so every participant first removes its kind's definitions, in
 * reverse import order, and then the bundle is applied. That is only safe while nothing refers to a definition, so
 * an organization for which any {@link InstanceDataProbe} counts instance data is refused before anything is
 * touched — and so is one that holds a domain starter, which the replacement would remove.
 *
 * <p>Every definition kind is written by the feature that owns it, through its
 * {@link DefinitionImportParticipant}, in the participants' {@code order()} and inside one
 * transaction: a bundle is applied completely or not at all. A dry run is that same transaction rolled
 * back, not a separate validation pass, because a later kind validates against an earlier one — a
 * state machine names an attribute of an entity the same bundle creates — and only writing the entity
 * makes that check pass.
 */
@Service
public class ImportBundle {

    private final OrganizationGuard guard;
    private final BundleReader reader;
    private final ImportParticipants participants;
    private final DefinitionProvenanceRepository provenanceRepository;
    private final InstalledStarterRepository installedStarterRepository;
    private final CommonRules commonRules;
    private final KeyPrefixRules keyPrefixRules;
    private final ObjectProvider<ModuleMountPort> mountPort;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ImportBundle(OrganizationGuard guard,
                        BundleReader reader,
                        ImportParticipants participants,
                        DefinitionProvenanceRepository provenanceRepository,
                        InstalledStarterRepository installedStarterRepository,
                        CommonRules commonRules,
                        KeyPrefixRules keyPrefixRules,
                        ObjectProvider<ModuleMountPort> mountPort,
                        PlatformTransactionManager transactionManager,
                        ObjectProvider<Clock> clock) {
        this.guard = guard;
        this.reader = reader;
        this.participants = participants;
        this.provenanceRepository = provenanceRepository;
        this.installedStarterRepository = installedStarterRepository;
        this.commonRules = commonRules;
        this.keyPrefixRules = keyPrefixRules;
        this.mountPort = mountPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /**
     * @param importedBy who is importing, recorded in provenance; may be null
     * @throws ImportRejectedException if the bundle or any of its definitions is refused, or the
     *                                 organization holds instance data; nothing is written then
     */
    public ImportReport execute(String orgKey, InputStream input, boolean dryRun, String importedBy) {
        return execute(orgKey, input, dryRun, importedBy, null);
    }

    /**
     * As {@link #execute(String, InputStream, boolean, String)}, refusing a bundle whose manifest is
     * not the starter the caller asked for — a catalog entry that names the wrong bundle.
     *
     * @param expected the starter id and version the bundle must declare; null to accept any
     */
    public ImportReport execute(String orgKey, InputStream input, boolean dryRun, String importedBy, Expected expected) {
        guard.requireDesign(orgKey);
        return importAuthorized(orgKey, input, dryRun, importedBy, expected);
    }

    /**
     * The import without the design-rights check, for {@link InstallStarter}, which has made the check
     * itself, or deliberately skips it for a provisioning call. Package-private so no other feature can
     * import bytes unchecked.
     */
    ImportReport importAuthorized(String orgKey, InputStream input, boolean dryRun, String importedBy, Expected expected) {
        StarterBundle bundle;
        try {
            bundle = reader.read(input);
        } catch (BundleRejectedException e) {
            throw new ImportRejectedException(
                    ImportReport.rejected(dryRun, null, null, List.of(new ImportReport.Error(e.getFile(), e.getMessage()))),
                    e.getReason() == BundleRejectedException.Reason.TOO_LARGE
                            ? ImportRejectedException.Reason.TOO_LARGE
                            : ImportRejectedException.Reason.INVALID);
        }
        StarterManifest manifest = bundle.manifest();

        List<ImportReport.Error> errors = new ArrayList<>();
        checkExpected(manifest, expected, errors);
        errors.addAll(commonRules.check(orgKey, manifest));
        Map<DefinitionImportParticipant, List<String>> plan = plan(manifest, errors);
        errors.addAll(keyPrefixRules.check(bundle));
        if (!errors.isEmpty()) {
            throw rejected(dryRun, manifest, errors, ImportRejectedException.Reason.INVALID);
        }
        boolean additive = isAdditive(manifest);
        List<ImportReport.Error> conflicts = additive ? conflicts(orgKey, bundle, plan) : replacesDomainStarters(orgKey);
        if (!conflicts.isEmpty()) {
            throw rejected(dryRun, manifest, conflicts, ImportRejectedException.Reason.CONFLICT);
        }
        List<ImportReport.Error> instanceData = additive ? List.of() : instanceData(orgKey);
        if (!instanceData.isEmpty()) {
            throw rejected(dryRun, manifest, instanceData, ImportRejectedException.Reason.HOLDS_INSTANCE_DATA);
        }

        ImportReport report = transactionTemplate.execute(status ->
                apply(orgKey, bundle, plan, additive, dryRun, importedBy, status));
        if (report.status() == ImportReport.Status.REJECTED) {
            throw new ImportRejectedException(report, ImportRejectedException.Reason.INVALID);
        }
        return report;
    }

    /**
     * Applies the bundle's files kind by kind, then its record files, appending what each wrote to {@code items}. Stops at
     * the first refusal and answers its errors: a participant that failed mid-write may have left the persistence context
     * in a state later participants should not build on. Empty when every file was written.
     */
    private Optional<List<ImportReport.Error>> write(String orgKey, StarterBundle bundle,
                                                     Map<DefinitionImportParticipant, List<String>> plan,
                                                     List<String> recordFiles,
                                                     Map<String, Set<String>> removed, List<ImportReport.Item> items) {
        for (Map.Entry<DefinitionImportParticipant, List<String>> step : plan.entrySet()) {
            DefinitionImportParticipant participant = step.getKey();
            Set<String> removedOfKind = removed.computeIfAbsent(participant.kind(), kind -> new LinkedHashSet<>());
            for (String file : step.getValue()) {
                ParticipantResult result = participant.apply(orgKey, file, bundle.file(file));
                if (result.isRejected()) {
                    return Optional.of(result.errors().stream().map(message -> new ImportReport.Error(file, message)).toList());
                }
                for (ImportedItem item : result.items()) {
                    boolean existed = removedOfKind.remove(item.key()) || item.action() == ImportedItem.Action.UPDATE;
                    items.add(new ImportReport.Item(participant.kind(), item.key(),
                            existed ? ImportReport.Action.UPDATE : ImportReport.Action.CREATE));
                }
            }
        }
        return writeRecords(orgKey, bundle, recordFiles, items);
    }

    /** Loads the record files after the definitions; the first refused file is the answer, as for a definition. */
    private Optional<List<ImportReport.Error>> writeRecords(String orgKey, StarterBundle bundle, List<String> recordFiles,
                                                            List<ImportReport.Item> items) {
        for (String file : recordFiles) {
            ParticipantResult result = participants.records().orElseThrow().load(orgKey, file, bundle.file(file));
            if (result.isRejected()) {
                return Optional.of(result.errors().stream().map(message -> new ImportReport.Error(file, message)).toList());
            }
            result.items().forEach(item ->
                    items.add(new ImportReport.Item(ImportReport.RECORD, item.key(), ImportReport.Action.CREATE)));
        }
        return Optional.empty();
    }

    /** Whether the bundle is added to the organization rather than replacing its definitions. */
    static boolean isAdditive(StarterManifest manifest) {
        return manifest.isDomainStarter() && !manifest.isCommon();
    }

    private ImportReport apply(String orgKey, StarterBundle bundle, Map<DefinitionImportParticipant, List<String>> plan,
                               boolean additive, boolean dryRun, String importedBy, TransactionStatus status) {
        StarterManifest manifest = bundle.manifest();
        Map<String, Set<String>> removed = additive ? new LinkedHashMap<>() : removeAll(orgKey);

        List<ImportReport.Item> items = new ArrayList<>();
        Optional<List<ImportReport.Error>> refusal = write(orgKey, bundle, plan, recordFiles(manifest), removed, items);
        if (refusal.isPresent()) {
            status.setRollbackOnly();
            return ImportReport.rejected(dryRun, manifest.id(), manifest.version(), refusal.get());
        }
        removed.forEach((kind, keys) -> keys.forEach(key -> items.add(new ImportReport.Item(kind, key, ImportReport.Action.DELETE))));
        List<String> warnings = additive ? mount(orgKey, manifest, items) : List.of();

        if (dryRun) {
            status.setRollbackOnly();
            return new ImportReport(true, ImportReport.Status.WOULD_APPLY, manifest.id(), manifest.version(), items, List.of(), warnings);
        }
        recordProvenance(orgKey, manifest, items, additive, importedBy);
        commonRules.commonInstalled(manifest);
        return new ImportReport(false, ImportReport.Status.APPLIED, manifest.id(), manifest.version(), items, List.of(), warnings);
    }

    /**
     * Mounts the modules the manifest's {@code mounts} name into the organization's application, through the port
     * {@code base-app} provides. Never a refusal: the modules are installed either way, so what could not be mounted, and
     * what is left for the application's owner to release, comes back as warnings.
     */
    private List<String> mount(String orgKey, StarterManifest manifest, List<ImportReport.Item> items) {
        if (manifest.mounts().isEmpty()) {
            return List.of();
        }
        ModuleMountPort port = mountPort.getIfAvailable();
        if (port == null) {
            return List.of("The starter's modules are installed but not mounted: this application cannot mount modules.");
        }
        List<ModuleMountPort.MountHint> hints = manifest.mounts().stream().map(ImportBundle::hintOf).toList();
        ModuleMountPort.MountOutcome outcome = port.mount(orgKey, hints);
        outcome.done().forEach(key -> items.add(new ImportReport.Item(ImportReport.MOUNT, key, ImportReport.Action.CREATE)));
        List<String> warnings = new ArrayList<>();
        outcome.skipped().forEach((key, reason) ->
                warnings.add("The module '" + key + "' is installed but not mounted: " + reason + "."));
        if (!outcome.done().isEmpty() && !outcome.released()) {
            warnings.add("The mounts are drafts of the application; release it to show them.");
        }
        return warnings;
    }

    private static ModuleMountPort.MountHint hintOf(StarterManifest.Mount mount) {
        ModuleMountPort.Nav nav = mount.nav() == null ? null : new ModuleMountPort.Nav(mount.nav().icon(), mount.nav().labelKey());
        StarterManifest.Launcher launcher = mount.launcher();
        return new ModuleMountPort.MountHint(mount.moduleKey(), mount.basePath(), nav, launcher == null ? null
                : new ModuleMountPort.Launcher(launcher.image(), launcher.titleKey(), launcher.subtitleKey(), launcher.contentKeys(), launcher.link()));
    }

    /** Every participant's definitions, dependents first; by kind, the keys that were removed. */
    private Map<String, Set<String>> removeAll(String orgKey) {
        Map<String, Set<String>> removed = new LinkedHashMap<>();
        for (DefinitionImportParticipant participant : participants.inRemovalOrder()) {
            removed.put(participant.kind(), new LinkedHashSet<>(participant.removeAll(orgKey)));
        }
        return removed;
    }

    /**
     * What stops a domain starter from being added: it is installed already, or a definition it brings exists in the
     * organization — the customer's own, or another starter's. Keys are never rewritten, so the install is refused
     * whole and every collision is listed, which is what a dry run is for. Translations and assets belong to a module,
     * whose key is checked.
     */
    private List<ImportReport.Error> conflicts(String orgKey, StarterBundle bundle,
                                               Map<DefinitionImportParticipant, List<String>> plan) {
        String starterId = bundle.manifest().id();
        Optional<InstalledStarter> installed = installedStarterRepository.findByOrgKeyAndStarterId(orgKey, starterId);
        if (installed.isPresent()) {
            return List.of(new ImportReport.Error(BundleReader.MANIFEST, installed.get().isDomainStarter()
                    ? "The starter '" + starterId + "' is installed already; upgrade it instead."
                    : "The starter '" + starterId + "' is installed here as an application of its own (version " + installed.get().getVersion()
                            + "), which version " + bundle.manifest().version() + " no longer is: its modules are added to the organization's own application. "
                            + "The two cannot be exchanged by an upgrade; use a new organization for version " + bundle.manifest().version() + "."));
        }
        List<ImportReport.Error> conflicts = new ArrayList<>();
        plan.forEach((participant, files) -> {
            if (DefinitionKinds.TRANSLATION.equals(participant.kind()) || DefinitionKinds.ASSET.equals(participant.kind())) {
                return;
            }
            for (String file : files) {
                Set<String> keys = KeyPrefixRules.keysIn(participant, file, bundle.file(file));
                if (keys.isEmpty()) {
                    continue;
                }
                for (String key : participant.fingerprints(orgKey, keys).keySet()) {
                    String owner = provenanceRepository.findByOrgKeyAndKindAndKey(orgKey, participant.kind(), key)
                            .map(provenance -> "the starter '" + provenance.getStarterId() + "'")
                            .orElse("the organization");
                    conflicts.add(new ImportReport.Error(file, "The " + participant.kind() + " '" + key
                            + "' exists already; it belongs to " + owner + "."));
                }
            }
        });
        return conflicts;
    }

    /** A replacing install would remove the definitions of every domain starter the organization holds. */
    private List<ImportReport.Error> replacesDomainStarters(String orgKey) {
        List<String> held = installedStarterRepository.findByOrgKeyOrderByStarterId(orgKey).stream()
                .filter(InstalledStarter::isDomainStarter)
                .map(InstalledStarter::getStarterId)
                .toList();
        if (held.isEmpty()) {
            return List.of();
        }
        return List.of(new ImportReport.Error(BundleReader.MANIFEST, "This starter replaces the organization's definitions, "
                + "which would remove the domain starters " + String.join(", ", held) + "; uninstall them first."));
    }

    private List<ImportReport.Error> instanceData(String orgKey) {
        List<String> held = new ArrayList<>();
        for (InstanceDataProbe probe : participants.probes()) {
            long count = probe.count(orgKey);
            if (count > 0) {
                held.add(count + " " + probe.label());
            }
        }
        if (held.isEmpty()) {
            return List.of();
        }
        return List.of(new ImportReport.Error(null, "A starter replaces the organization's definitions, so it can only "
                + "be installed while the organization holds no instance data; it holds " + String.join(", ", held) + "."));
    }

    private void checkExpected(StarterManifest manifest, Expected expected, List<ImportReport.Error> errors) {
        if (expected != null && (!expected.starterId().equals(manifest.id()) || !expected.version().equals(manifest.version()))) {
            errors.add(new ImportReport.Error(BundleReader.MANIFEST, "The bundle is starter '" + manifest.id() + "' "
                    + manifest.version() + ", not '" + expected.starterId() + "' " + expected.version() + "."));
        }
    }

    /** The seed-record files, loaded after the definitions. Their group is checked by {@link #plan}. */
    private static List<String> recordFiles(StarterManifest manifest) {
        return manifest.contents().getOrDefault(ContentGroups.RECORDS, List.of());
    }

    /** Participants in import order, each with the bundle files of its kind. */
    private Map<DefinitionImportParticipant, List<String>> plan(StarterManifest manifest, List<ImportReport.Error> errors) {
        Map<String, List<String>> filesByKind = new LinkedHashMap<>();
        manifest.contents().forEach((group, files) -> {
            if (ContentGroups.RECORDS.equals(group)) {
                if (!files.isEmpty() && participants.records().isEmpty()) {
                    errors.add(new ImportReport.Error(BundleReader.MANIFEST, "This platform cannot import 'records' yet."));
                }
                return;
            }
            Optional<String> kind = ContentGroups.kindOf(group);
            if (kind.isEmpty()) {
                errors.add(new ImportReport.Error(BundleReader.MANIFEST, "Unknown contents group '" + group + "'."));
            } else if (!files.isEmpty() && participants.forKind(kind.get()).isEmpty()) {
                errors.add(new ImportReport.Error(BundleReader.MANIFEST,
                        "This platform cannot import '" + group + "' definitions yet."));
            } else {
                filesByKind.computeIfAbsent(kind.get(), k -> new ArrayList<>()).addAll(files);
            }
        });

        Map<DefinitionImportParticipant, List<String>> plan = new LinkedHashMap<>();
        participants.inOrder().stream()
                .filter(participant -> filesByKind.containsKey(participant.kind()))
                .forEach(participant -> plan.put(participant, filesByKind.get(participant.kind())));
        return plan;
    }

    /**
     * Replaces the organization's provenance with this starter's: the definitions an earlier starter
     * brought are gone. A domain starter only adds its own rows. Hashes each definition's fingerprint taken
     * <em>after</em> the write, so the stored hash describes what the database holds rather than the bundle's text —
     * two spellings of one definition are the same definition.
     */
    private void recordProvenance(String orgKey, StarterManifest manifest, List<ImportReport.Item> items, boolean additive,
                                  String importedBy) {
        if (!additive) {
            provenanceRepository.deleteByOrgKey(orgKey);
            installedStarterRepository.deleteByOrgKey(orgKey);
            // Flushed now: Hibernate orders inserts before deletes, and the rows below reuse the keys.
            provenanceRepository.flush();
        }

        Instant now = clock.instant();
        Map<String, Set<String>> keysByKind = items.stream()
                .filter(item -> item.action() != ImportReport.Action.DELETE && !ImportReport.MOUNT.equals(item.kind())
                        && !ImportReport.RECORD.equals(item.kind()))
                .collect(Collectors.groupingBy(
                        ImportReport.Item::kind, LinkedHashMap::new,
                        Collectors.mapping(ImportReport.Item::key, Collectors.toCollection(LinkedHashSet::new))));

        keysByKind.forEach((kind, keys) -> {
            Map<String, String> fingerprints = participants.forKind(kind).orElseThrow().fingerprints(orgKey, keys);
            for (String key : keys) {
                String fingerprint = fingerprints.get(key);
                if (fingerprint == null) {
                    throw new IllegalStateException(
                            "The " + kind + " participant imported '" + key + "' but reports no fingerprint for it.");
                }
                // A row can outlive its definition when a designer deleted it; this install owns the key now.
                DefinitionProvenance provenance = provenanceRepository.findByOrgKeyAndKindAndKey(orgKey, kind, key)
                        .orElseGet(() -> new DefinitionProvenance(orgKey, kind, key));
                provenance.recordImport(manifest.id(), manifest.version(), hash(fingerprint), now, importedBy);
                provenanceRepository.save(provenance);
            }
        });

        InstalledStarter installed = new InstalledStarter(orgKey, manifest.id());
        if (additive) {
            installed.markDomainStarter();
        }
        installed.recordInstall(manifest.version(), now, importedBy);
        installedStarterRepository.save(installed);
    }

    static String hash(String fingerprint) {
        return BundleReader.sha256(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private static ImportRejectedException rejected(boolean dryRun, StarterManifest manifest, List<ImportReport.Error> errors,
                                                    ImportRejectedException.Reason reason) {
        return new ImportRejectedException(ImportReport.rejected(dryRun, manifest.id(), manifest.version(), errors), reason);
    }

    /** The starter a caller asked for, which the bundle's manifest must declare. */
    public record Expected(String starterId, String version) {
    }
}
