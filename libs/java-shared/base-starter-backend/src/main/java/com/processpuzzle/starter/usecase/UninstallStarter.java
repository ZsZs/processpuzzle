package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionRemoval;
import com.processpuzzle.core.definition.ModuleMountPort;
import com.processpuzzle.core.definition.InstanceDataProbe;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarter;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes one domain starter from one organization again: the definitions it brought, as recorded in provenance, and
 * nothing else — not what the customer made, not what other starters brought.
 *
 * <p>All or nothing, like an install. It is refused (409) while the organization holds instance data of the starter's
 * own definitions — entity objects of its entity types, instances of its workflows, asked through the
 * {@link InstanceDataProbe}s' keyed count — and while any definition of the starter is still needed by something else,
 * which the feature's own delete refuses with its reason. Nothing is written then, and the report lists every reason, so
 * a dry run shows what stands in the way.
 *
 * <p>The definitions go the way the designer's delete does, as {@link DefinitionImportParticipant#removeDrafts}: the
 * drafts are removed and the released rows follow with the organization's next release, which is what an upgrade does
 * with a definition a newer version drops. A starter that replaced the organization's definitions (a bundle below
 * manifestVersion 3) and a common are never uninstalled.
 */
@Service
public class UninstallStarter {

    private final OrganizationGuard guard;
    private final ImportParticipants participants;
    private final DefinitionProvenanceRepository provenanceRepository;
    private final InstalledStarterRepository installedRepository;
    private final ObjectProvider<ModuleMountPort> mountPort;
    private final TransactionTemplate transactionTemplate;

    public UninstallStarter(OrganizationGuard guard, ImportParticipants participants,
                            DefinitionProvenanceRepository provenanceRepository, InstalledStarterRepository installedRepository,
                            ObjectProvider<ModuleMountPort> mountPort, PlatformTransactionManager transactionManager) {
        this.guard = guard;
        this.participants = participants;
        this.provenanceRepository = provenanceRepository;
        this.installedRepository = installedRepository;
        this.mountPort = mountPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws StarterNotFoundException  if the organization has not installed the starter
     * @throws ImportRejectedException   if the starter cannot be removed, or something stands in the way; nothing is written
     */
    public ImportReport execute(String orgKey, String starterId, boolean dryRun) {
        guard.requireDesign(orgKey);
        InstalledStarter installed = installedRepository.findByOrgKeyAndStarterId(orgKey, starterId)
                .orElseThrow(() -> new StarterNotFoundException(
                        "The organization has not installed the starter '" + starterId + "'."));
        String version = installed.getVersion();
        if (!installed.isDomainStarter()) {
            throw rejected(dryRun, starterId, version, ImportRejectedException.Reason.INVALID, List.of(new ImportReport.Error(null,
                    "The starter '" + starterId + "' replaced the organization's definitions when it was installed, so it "
                            + "cannot be removed; delete its definitions one by one, or install another starter over it.")));
        }

        Map<String, Set<String>> owned = owned(orgKey, starterId);
        List<ImportReport.Error> data = instanceData(orgKey, owned);
        if (!data.isEmpty()) {
            throw rejected(dryRun, starterId, version, ImportRejectedException.Reason.HOLDS_INSTANCE_DATA, data);
        }

        ImportReport report = transactionTemplate.execute(status -> {
            List<ImportReport.Item> items = new ArrayList<>();
            List<ImportReport.Error> kept = remove(orgKey, owned, items);
            if (!kept.isEmpty()) {
                status.setRollbackOnly();
                return ImportReport.rejected(dryRun, starterId, version, kept);
            }
            List<String> warnings = unmount(orgKey, owned, items);
            if (dryRun) {
                status.setRollbackOnly();
                return new ImportReport(true, ImportReport.Status.WOULD_APPLY, starterId, version, items, List.of(), warnings);
            }
            provenanceRepository.deleteByOrgKeyAndStarterId(orgKey, starterId);
            installedRepository.delete(installed);
            return new ImportReport(false, ImportReport.Status.APPLIED, starterId, version, items, List.of(), warnings);
        });
        if (report.status() == ImportReport.Status.REJECTED) {
            throw new ImportRejectedException(report, ImportRejectedException.Reason.CONFLICT);
        }
        return report;
    }

    /** The starter's definition keys by kind, from provenance. */
    private Map<String, Set<String>> owned(String orgKey, String starterId) {
        Map<String, Set<String>> owned = new LinkedHashMap<>();
        for (DefinitionProvenance row : provenanceRepository.findByOrgKeyAndStarterId(orgKey, starterId)) {
            owned.computeIfAbsent(row.getKind(), kind -> new LinkedHashSet<>()).add(row.getKey());
        }
        return owned;
    }

    private List<ImportReport.Error> instanceData(String orgKey, Map<String, Set<String>> owned) {
        List<String> held = new ArrayList<>();
        for (InstanceDataProbe probe : participants.probes()) {
            long count;
            if (probe.definitionKind() == null) {
                count = probe.count(orgKey); // cannot tell the starter's data apart: any data stops the removal
            } else {
                Collection<String> keys = owned.getOrDefault(probe.definitionKind(), Set.of());
                count = keys.isEmpty() ? 0 : probe.count(orgKey, keys);
            }
            if (count > 0) {
                held.add(count + " " + probe.label());
            }
        }
        return held.isEmpty() ? List.of() : List.of(new ImportReport.Error(null, "The starter's definitions still have data: "
                + String.join(", ", held) + ". Delete the data first."));
    }

    /** Dependents first, as an install removes; every definition that stays is an error with the feature's reason. */
    private List<ImportReport.Error> remove(String orgKey, Map<String, Set<String>> owned, List<ImportReport.Item> items) {
        List<ImportReport.Error> kept = new ArrayList<>();
        for (DefinitionImportParticipant participant : participants.inRemovalOrder()) {
            Set<String> keys = owned.getOrDefault(participant.kind(), Set.of());
            if (keys.isEmpty()) {
                continue;
            }
            if (!participant.supportsUpgrade()) {
                kept.add(new ImportReport.Error(null, "The " + participant.kind() + " definitions of the starter cannot be removed "
                        + "one by one on this platform."));
                continue;
            }
            DefinitionRemoval removal = participant.removeDrafts(orgKey, keys);
            removal.removed().forEach(key ->
                    items.add(new ImportReport.Item(participant.kind(), key, ImportReport.Action.DELETE)));
            removal.refused().forEach((key, reason) -> kept.add(new ImportReport.Error(null,
                    "The " + participant.kind() + " '" + key + "' stays: " + reason)));
        }
        return kept;
    }

    /** Takes the starter's modules out of the application's mounts and sidenav, as the install put them in. */
    private List<String> unmount(String orgKey, Map<String, Set<String>> owned, List<ImportReport.Item> items) {
        Set<String> modules = owned.getOrDefault(DefinitionKinds.MODULE, Set.of());
        ModuleMountPort port = mountPort.getIfAvailable();
        if (modules.isEmpty() || port == null) {
            return List.of();
        }
        ModuleMountPort.MountOutcome outcome = port.unmount(orgKey, modules);
        outcome.done().forEach(key -> items.add(new ImportReport.Item(ImportReport.MOUNT, key, ImportReport.Action.DELETE)));
        return !outcome.done().isEmpty() && !outcome.released()
                ? List.of("The mounts are removed from the draft of the application; release it to remove them from the live one.")
                : List.of();
    }

    private static ImportRejectedException rejected(boolean dryRun, String starterId, String version,
                                                    ImportRejectedException.Reason reason, List<ImportReport.Error> errors) {
        return new ImportRejectedException(ImportReport.rejected(dryRun, starterId, version, errors), reason);
    }
}
