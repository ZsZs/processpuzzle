package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.InstanceDataProbe;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link DefinitionImportParticipant} beans the deploying application contributes, one per kind,
 * and its {@link InstanceDataProbe}s. Which kinds a deployment can import, and which instance data it
 * can hold, depends only on which feature libraries it hosts.
 */
@Component
public class ImportParticipants {

    private final Map<String, DefinitionImportParticipant> byKind = new LinkedHashMap<>();
    private final List<InstanceDataProbe> probes;

    @Autowired
    public ImportParticipants(ObjectProvider<DefinitionImportParticipant> participants,
                              ObjectProvider<InstanceDataProbe> probes) {
        this(participants.stream().toList(), probes.stream().toList());
    }

    private ImportParticipants(List<DefinitionImportParticipant> participants, List<InstanceDataProbe> probes) {
        participants.stream()
                .sorted(Comparator.comparingInt(DefinitionImportParticipant::order))
                .forEach(this::add);
        this.probes = List.copyOf(probes);
    }

    /** For a caller that holds the participants already, such as a test. */
    public static ImportParticipants of(List<DefinitionImportParticipant> participants) {
        return of(participants, List.of());
    }

    public static ImportParticipants of(List<DefinitionImportParticipant> participants, List<InstanceDataProbe> probes) {
        return new ImportParticipants(participants, probes);
    }

    public Optional<DefinitionImportParticipant> forKind(String kind) {
        return Optional.ofNullable(byKind.get(kind));
    }

    /** In import order. */
    public Collection<DefinitionImportParticipant> inOrder() {
        return byKind.values();
    }

    /** In reverse import order, the order definitions are removed in: dependents first. */
    public List<DefinitionImportParticipant> inRemovalOrder() {
        return List.copyOf(byKind.values()).reversed();
    }

    public List<InstanceDataProbe> probes() {
        return probes;
    }

    private void add(DefinitionImportParticipant participant) {
        DefinitionImportParticipant previous = byKind.putIfAbsent(participant.kind(), participant);
        if (previous != null) {
            throw new IllegalStateException("Two import participants for kind '" + participant.kind() + "': "
                    + previous.getClass().getName() + " and " + participant.getClass().getName());
        }
    }
}
