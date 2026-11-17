package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.starter.bundle.ContentGroups;
import com.processpuzzle.starter.bundle.StarterBundle;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Decides, definition by definition, what upgrading an organization to a newer bundle of its starter does — reading
 * only, so the same plan serves a dry run and the real thing. The provenance the importer recorded is what tells an
 * <em>untouched</em> definition (its current fingerprint still hashes to the recorded one) from a customized one:
 * <ul>
 *   <li>in the bundle, untouched in the organization → replaced by the bundle's;</li>
 *   <li>in the bundle, customized → kept, reported (the organization's version wins);</li>
 *   <li>in the bundle, absent from the organization → added, unless the organization deleted it, then left deleted;</li>
 *   <li>dropped from the bundle → removed if untouched, kept if customized.</li>
 * </ul>
 * A kind whose participant cannot work definition by definition is left as it is, and named.
 */
@Component
public class UpgradePlanner {

    private final ImportParticipants participants;
    private final DefinitionProvenanceRepository provenance;

    public UpgradePlanner(ImportParticipants participants, DefinitionProvenanceRepository provenance) {
        this.participants = participants;
        this.provenance = provenance;
    }

    /**
     * What happens to the definitions of one kind.
     *
     * @param writeByFile the keys to write, per bundle file: the added and the replaced
     * @param remove      the keys to remove: dropped from the bundle and untouched
     * @param stale       keys whose provenance is left although the organization no longer has the definition
     * @param items       the decisions, for the report; removals are listed once they have been tried
     */
    public record KindPlan(DefinitionImportParticipant participant, Map<String, Set<String>> writeByFile, Set<String> remove,
                           Set<String> stale, List<UpgradeReport.Item> items) {

        public Set<String> written() {
            return writeByFile.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /** @param skippedKinds kinds the bundle brings that cannot be upgraded definition by definition yet */
    public record Plan(List<KindPlan> kinds, List<String> skippedKinds) {
    }

    public Plan plan(String orgKey, StarterBundle bundle) {
        String starterId = bundle.manifest().id();
        Map<String, List<String>> filesByKind = filesByKind(bundle);
        Map<String, List<DefinitionProvenance>> knownByKind = provenance.findByOrgKeyAndStarterId(orgKey, starterId).stream()
            .collect(Collectors.groupingBy(DefinitionProvenance::getKind));
        List<KindPlan> kinds = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (DefinitionImportParticipant participant : participants.inOrder()) {
            List<String> files = filesByKind.getOrDefault(participant.kind(), List.of());
            List<DefinitionProvenance> known = knownByKind.getOrDefault(participant.kind(), List.of());
            if (files.isEmpty() && known.isEmpty()) {
                continue;
            }
            if (participant.supportsUpgrade()) {
                kinds.add(planKind(orgKey, bundle, participant, files, known));
            } else if (!files.isEmpty()) {
                skipped.add(participant.kind());
            }
        }
        return new Plan(kinds, skipped);
    }

    private KindPlan planKind(String orgKey, StarterBundle bundle, DefinitionImportParticipant participant, List<String> files,
                              List<DefinitionProvenance> known) {
        Map<String, Set<String>> keysByFile = new LinkedHashMap<>();
        files.forEach(file -> keysByFile.put(file, participant.keysIn(file, bundle.file(file))));
        Set<String> bundleKeys = keysByFile.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, DefinitionProvenance> knownByKey = known.stream().collect(Collectors.toMap(DefinitionProvenance::getKey, Function.identity()));

        Set<String> lookup = new LinkedHashSet<>(bundleKeys);
        lookup.addAll(knownByKey.keySet());
        Map<String, String> current = participant.fingerprints(orgKey, lookup);

        Map<String, Set<String>> writeByFile = new LinkedHashMap<>();
        List<UpgradeReport.Item> items = new ArrayList<>();
        keysByFile.forEach((file, keys) -> {
            Set<String> write = new LinkedHashSet<>();
            for (String key : keys) {
                UpgradeReport.Item item = decide(participant.kind(), key, knownByKey.get(key), current.get(key));
                items.add(item);
                if (item.action() == UpgradeReport.Action.ADD || item.action() == UpgradeReport.Action.REPLACE) {
                    write.add(key);
                }
            }
            writeByFile.put(file, write);
        });

        Set<String> remove = new LinkedHashSet<>();
        Set<String> stale = new LinkedHashSet<>();
        knownByKey.forEach((key, row) -> {
            if (!bundleKeys.contains(key)) {
                dropped(participant.kind(), row, current.get(key)).ifPresentOrElse(item -> {
                    if (item.action() == UpgradeReport.Action.REMOVE) {
                        remove.add(key);
                    } else {
                        items.add(item);
                    }
                }, () -> stale.add(key));
            }
        });
        return new KindPlan(participant, writeByFile, remove, stale, items);
    }

    private static UpgradeReport.Item decide(String kind, String key, DefinitionProvenance row, String fingerprint) {
        if (row == null) {
            return fingerprint == null
                ? new UpgradeReport.Item(kind, key, UpgradeReport.Action.ADD, null)
                : new UpgradeReport.Item(kind, key, UpgradeReport.Action.KEEP_CUSTOMIZED, "exists already, and was not installed by this starter");
        }
        if (fingerprint == null) {
            return new UpgradeReport.Item(kind, key, UpgradeReport.Action.KEEP_DELETED, "deleted by the organization; not brought back");
        }
        return untouched(row, fingerprint)
            ? new UpgradeReport.Item(kind, key, UpgradeReport.Action.REPLACE, null)
            : new UpgradeReport.Item(kind, key, UpgradeReport.Action.KEEP_CUSTOMIZED, "changed by the organization; its version stays");
    }

    /** A dropped definition: removed when untouched, kept when customized, nothing to do when it is gone already. */
    private static Optional<UpgradeReport.Item> dropped(String kind, DefinitionProvenance row, String fingerprint) {
        if (fingerprint == null) {
            return Optional.empty();
        }
        return Optional.of(untouched(row, fingerprint)
            ? new UpgradeReport.Item(kind, row.getKey(), UpgradeReport.Action.REMOVE, null)
            : new UpgradeReport.Item(kind, row.getKey(), UpgradeReport.Action.KEEP_CUSTOMIZED, "no longer in the starter, but changed by the organization"));
    }

    private static boolean untouched(DefinitionProvenance row, String fingerprint) {
        return ImportBundle.hash(fingerprint).equals(row.getContentHash());
    }

    private static Map<String, List<String>> filesByKind(StarterBundle bundle) {
        Map<String, List<String>> filesByKind = new LinkedHashMap<>();
        bundle.manifest().contents().forEach((group, files) -> ContentGroups.kindOf(group)
            .ifPresent(kind -> filesByKind.computeIfAbsent(kind, k -> new ArrayList<>()).addAll(files)));
        return filesByKind;
    }
}
