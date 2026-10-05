package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
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
 * The {@link DefinitionImportParticipant} beans the deploying application contributes, one per kind.
 * Which kinds a deployment can import depends only on which feature libraries it hosts.
 */
@Component
public class ImportParticipants {

    private final Map<String, DefinitionImportParticipant> byKind = new LinkedHashMap<>();

    @Autowired
    public ImportParticipants(ObjectProvider<DefinitionImportParticipant> participants) {
        this(participants.stream().toList());
    }

    private ImportParticipants(List<DefinitionImportParticipant> participants) {
        participants.stream()
                .sorted(Comparator.comparingInt(DefinitionImportParticipant::order))
                .forEach(this::add);
    }

    /** For a caller that holds the participants already, such as a test. */
    public static ImportParticipants of(List<DefinitionImportParticipant> participants) {
        return new ImportParticipants(participants);
    }

    public Optional<DefinitionImportParticipant> forKind(String kind) {
        return Optional.ofNullable(byKind.get(kind));
    }

    /** In import order. */
    public Collection<DefinitionImportParticipant> inOrder() {
        return byKind.values();
    }

    private void add(DefinitionImportParticipant participant) {
        DefinitionImportParticipant previous = byKind.putIfAbsent(participant.kind(), participant);
        if (previous != null) {
            throw new IllegalStateException("Two import participants for kind '" + participant.kind() + "': "
                    + previous.getClass().getName() + " and " + participant.getClass().getName());
        }
    }
}
