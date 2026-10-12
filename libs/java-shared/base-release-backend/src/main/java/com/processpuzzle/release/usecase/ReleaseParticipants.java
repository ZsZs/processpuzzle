package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.ReleaseParticipant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link ReleaseParticipant} beans the deploying application contributes, one per kind. Which
 * kinds a release carries depends only on which feature libraries the deployment hosts.
 */
@Component
public class ReleaseParticipants {

    private final Map<String, ReleaseParticipant> byKind = new LinkedHashMap<>();

    @Autowired
    public ReleaseParticipants(ObjectProvider<ReleaseParticipant> participants) {
        this(participants.stream().toList());
    }

    private ReleaseParticipants(List<ReleaseParticipant> participants) {
        participants.stream()
                .sorted(Comparator.comparingInt(ReleaseParticipant::order))
                .forEach(this::add);
    }

    /** For a caller that holds the participants already, such as a test. */
    public static ReleaseParticipants of(List<ReleaseParticipant> participants) {
        return new ReleaseParticipants(participants);
    }

    /** In release order. */
    public Collection<ReleaseParticipant> inOrder() {
        return byKind.values();
    }

    public boolean manages(String kind) {
        return byKind.containsKey(kind);
    }

    private void add(ReleaseParticipant participant) {
        ReleaseParticipant previous = byKind.putIfAbsent(participant.kind(), participant);
        if (previous != null) {
            throw new IllegalStateException("Two release participants for kind '" + participant.kind() + "': "
                    + previous.getClass().getName() + " and " + participant.getClass().getName());
        }
    }
}
