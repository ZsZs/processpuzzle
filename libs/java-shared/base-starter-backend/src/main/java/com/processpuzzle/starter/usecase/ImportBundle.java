package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
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
 * Imports one Business Starter bundle into one organization.
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
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ImportBundle(OrganizationGuard guard,
                        BundleReader reader,
                        ImportParticipants participants,
                        DefinitionProvenanceRepository provenanceRepository,
                        InstalledStarterRepository installedStarterRepository,
                        PlatformTransactionManager transactionManager,
                        ObjectProvider<Clock> clock) {
        this.guard = guard;
        this.reader = reader;
        this.participants = participants;
        this.provenanceRepository = provenanceRepository;
        this.installedStarterRepository = installedStarterRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /**
     * @param importedBy who is importing, recorded in provenance; may be null
     * @throws ImportRejectedException if the bundle or any of its definitions is refused; nothing is
     *                                 written then
     */
    public ImportReport execute(String orgKey, InputStream input, boolean dryRun, String importedBy) {
        guard.requireDesign(orgKey);

        StarterBundle bundle;
        try {
            bundle = reader.read(input);
        } catch (BundleRejectedException e) {
            throw new ImportRejectedException(
                    ImportReport.rejected(dryRun, null, null, List.of(new ImportReport.Error(e.getFile(), e.getMessage()))),
                    e.getReason() == BundleRejectedException.Reason.TOO_LARGE);
        }
        StarterManifest manifest = bundle.manifest();

        List<ImportReport.Error> errors = new ArrayList<>();
        Map<DefinitionImportParticipant, List<String>> plan = plan(manifest, errors);
        checkRequirements(orgKey, manifest, errors);
        if (!errors.isEmpty()) {
            throw rejected(dryRun, manifest, errors);
        }

        ImportReport report = transactionTemplate.execute(status ->
                apply(orgKey, bundle, plan, dryRun, importedBy, status));
        if (report.status() == ImportReport.Status.REJECTED) {
            throw new ImportRejectedException(report, false);
        }
        return report;
    }

    private ImportReport apply(String orgKey, StarterBundle bundle, Map<DefinitionImportParticipant, List<String>> plan,
                               boolean dryRun, String importedBy, TransactionStatus status) {
        StarterManifest manifest = bundle.manifest();
        List<ImportReport.Item> items = new ArrayList<>();
        for (Map.Entry<DefinitionImportParticipant, List<String>> step : plan.entrySet()) {
            DefinitionImportParticipant participant = step.getKey();
            for (String file : step.getValue()) {
                ParticipantResult result = participant.apply(orgKey, file, bundle.file(file));
                if (result.isRejected()) {
                    // Stop at the first refusal: a participant that failed mid-write may have left the
                    // persistence context in a state later participants should not build on.
                    status.setRollbackOnly();
                    List<ImportReport.Error> errors = result.errors().stream()
                            .map(message -> new ImportReport.Error(file, message))
                            .toList();
                    return ImportReport.rejected(dryRun, manifest.id(), manifest.version(), errors);
                }
                result.items().forEach(item -> items.add(new ImportReport.Item(participant.kind(), item.key(), item.action())));
            }
        }

        if (dryRun) {
            status.setRollbackOnly();
            return new ImportReport(true, ImportReport.Status.WOULD_APPLY, manifest.id(), manifest.version(), items, List.of());
        }
        recordProvenance(orgKey, manifest, items, importedBy);
        return new ImportReport(false, ImportReport.Status.APPLIED, manifest.id(), manifest.version(), items, List.of());
    }

    /** Participants in import order, each with the bundle files of its kind. */
    private Map<DefinitionImportParticipant, List<String>> plan(StarterManifest manifest, List<ImportReport.Error> errors) {
        Map<String, List<String>> filesByKind = new LinkedHashMap<>();
        manifest.contents().forEach((group, files) -> {
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

    /** Resolving and installing required starters is not implemented yet; they must already be installed. */
    private void checkRequirements(String orgKey, StarterManifest manifest, List<ImportReport.Error> errors) {
        for (StarterManifest.Requirement requirement : manifest.requires()) {
            if (requirement.id() == null
                    || installedStarterRepository.findByOrgKeyAndStarterId(orgKey, requirement.id()).isEmpty()) {
                errors.add(new ImportReport.Error(BundleReader.MANIFEST,
                        "Requires starter '" + requirement.id() + "', which is not installed in this organization."));
            }
        }
    }

    /**
     * Hashes each definition's fingerprint taken <em>after</em> the write, so the stored hash describes
     * what the database holds rather than the bundle's text — two spellings of one definition are the
     * same definition.
     */
    private void recordProvenance(String orgKey, StarterManifest manifest, List<ImportReport.Item> items, String importedBy) {
        Instant now = clock.instant();
        Map<String, Set<String>> keysByKind = items.stream().collect(Collectors.groupingBy(
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
                DefinitionProvenance provenance = provenanceRepository.findByOrgKeyAndKindAndKey(orgKey, kind, key)
                        .orElseGet(() -> new DefinitionProvenance(orgKey, kind, key));
                provenance.recordImport(manifest.id(), manifest.version(), hash(fingerprint), now, importedBy);
                provenanceRepository.save(provenance);
            }
        });

        InstalledStarter installed = installedStarterRepository.findByOrgKeyAndStarterId(orgKey, manifest.id())
                .orElseGet(() -> new InstalledStarter(orgKey, manifest.id()));
        installed.recordInstall(manifest.version(), now, importedBy);
        installedStarterRepository.save(installed);
    }

    static String hash(String fingerprint) {
        return BundleReader.sha256(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private static ImportRejectedException rejected(boolean dryRun, StarterManifest manifest, List<ImportReport.Error> errors) {
        return new ImportRejectedException(ImportReport.rejected(dryRun, manifest.id(), manifest.version(), errors), false);
    }

}
