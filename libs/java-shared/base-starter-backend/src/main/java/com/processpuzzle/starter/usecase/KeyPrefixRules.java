package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.ContentGroups;
import com.processpuzzle.starter.bundle.StarterBundle;
import com.processpuzzle.starter.bundle.StarterManifest;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The key-prefix convention of a domain starter ({@code manifestVersion: 3}, business-starters design §11.2): every
 * definition key the bundle holds starts with the manifest's {@code keyPrefix} and a dash, and every mount names one
 * of the bundle's modules. Several starters share one organization, and the prefix is what keeps their keys apart.
 *
 * <p>The keys are each feature's own, asked through {@link DefinitionImportParticipant#keysIn}, so no definition
 * format is parsed here. Translations and assets are keyed by the module that owns them and are left out; a file a
 * participant cannot read is left to the import, which reports it with the participant's own message.
 */
@Component
public class KeyPrefixRules {

    private static final Set<String> OWNED_KINDS = Set.of(DefinitionKinds.TRANSLATION, DefinitionKinds.ASSET);

    private final ImportParticipants participants;

    public KeyPrefixRules(ImportParticipants participants) {
        this.participants = participants;
    }

    /** What breaks the convention in {@code bundle}; empty when nothing does, or the bundle is not a domain starter. */
    public List<ImportReport.Error> check(StarterBundle bundle) {
        StarterManifest manifest = bundle.manifest();
        if (!manifest.isDomainStarter()) {
            return List.of();
        }
        String prefix = manifest.keyPrefix() + "-";
        List<ImportReport.Error> errors = new ArrayList<>();
        Set<String> modules = new LinkedHashSet<>();
        manifest.contents().forEach((group, files) -> {
            Optional<DefinitionImportParticipant> participant = ContentGroups.kindOf(group)
                    .filter(kind -> !OWNED_KINDS.contains(kind))
                    .flatMap(participants::forKind);
            if (participant.isEmpty()) {
                return; // an owned kind, or a group the import refuses on its own
            }
            for (String file : files) {
                Set<String> keys = keysIn(participant.get(), file, bundle.file(file));
                keys.stream()
                        .filter(key -> !key.startsWith(prefix))
                        .forEach(key -> errors.add(new ImportReport.Error(file, "The " + participant.get().kind() + " '" + key
                                + "' does not start with the starter's key prefix '" + prefix + "'.")));
                if (DefinitionKinds.MODULE.equals(participant.get().kind())) {
                    modules.addAll(keys);
                }
            }
        });
        manifest.mounts().stream()
                .map(StarterManifest.Mount::moduleKey)
                .filter(key -> !modules.contains(key))
                .forEach(key -> errors.add(new ImportReport.Error(BundleReader.MANIFEST,
                        "A mount names the module '" + key + "', which the bundle does not define.")));
        return errors;
    }

    /** The keys {@code participant} reads from {@code content}; none for a file it cannot read, which the import reports. */
    static Set<String> keysIn(DefinitionImportParticipant participant, String file, byte[] content) {
        try {
            return participant.keysIn(file, content);
        } catch (UncheckedIOException | IllegalArgumentException | UnsupportedOperationException e) {
            return Set.of();
        }
    }
}
