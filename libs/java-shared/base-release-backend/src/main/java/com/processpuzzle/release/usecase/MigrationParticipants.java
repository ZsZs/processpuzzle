package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.MigrationParticipant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The {@link MigrationParticipant} beans the deploying application contributes, one per kind. */
@Component
public class MigrationParticipants {

    private final Map<String, MigrationParticipant> byKind = new LinkedHashMap<>();

    @Autowired
    public MigrationParticipants(ObjectProvider<MigrationParticipant> participants) {
        this(participants.stream().toList());
    }

    private MigrationParticipants(List<MigrationParticipant> participants) {
        participants.forEach(this::add);
    }

    /** For a caller that holds the participants already, such as a test. */
    public static MigrationParticipants of(List<MigrationParticipant> participants) {
        return new MigrationParticipants(participants);
    }

    /** The participant that runs {@code operation} on objects of {@code kind}, if there is one. */
    public Optional<MigrationParticipant> running(String kind, String operation) {
        return Optional.ofNullable(byKind.get(kind)).filter(participant -> participant.operations().contains(operation));
    }

    private void add(MigrationParticipant participant) {
        MigrationParticipant previous = byKind.putIfAbsent(participant.kind(), participant);
        if (previous != null) {
            throw new IllegalStateException("Two migration participants for kind '" + participant.kind() + "': "
                    + previous.getClass().getName() + " and " + participant.getClass().getName());
        }
    }
}
