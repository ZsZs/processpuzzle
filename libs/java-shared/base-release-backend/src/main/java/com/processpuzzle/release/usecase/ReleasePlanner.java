package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Works out a {@link PendingRelease}: asks every participant, in order, what it would change and what
 * its drafts refer to, resolves each reference against every participant's drafts — by any key they may be
 * referred to by ({@link ReleaseParticipant#referableKeys}) — and collects what the participants' own checks
 * object to.
 *
 * <p>A reference into a kind no participant releases is not checked: until a feature takes part in
 * releases, its definitions go live as they are saved, so there is no draft to miss.
 */
@Component
class ReleasePlanner {

    static final String UNRESOLVED_REFERENCE = "release.unresolved-reference";

    private final ReleaseParticipants participants;

    ReleasePlanner(ReleaseParticipants participants) {
        this.participants = participants;
    }

    PendingRelease plan(String orgKey) {
        Map<String, Set<String>> drafts = new HashMap<>();
        List<PendingChange> changes = new ArrayList<>();
        for (ReleaseParticipant participant : participants.inOrder()) {
            drafts.put(participant.kind(), participant.referableKeys(orgKey));
            changes.addAll(participant.pending(orgKey));
        }
        ReleaseCandidate candidate = new ReleaseCandidate() {
            @Override
            public boolean exists(String kind, String key) {
                return drafts.getOrDefault(kind, Set.of()).contains(key);
            }

            @Override
            public boolean manages(String kind) {
                return participants.manages(kind);
            }
        };

        List<ReleaseProblem> problems = new ArrayList<>();
        for (ReleaseParticipant participant : participants.inOrder()) {
            for (DefinitionReference reference : participant.references(orgKey)) {
                if (candidate.manages(reference.toKind()) && !candidate.exists(reference.toKind(), reference.toKey())) {
                    problems.add(unresolved(reference));
                }
            }
            problems.addAll(participant.check(orgKey, candidate));
        }
        return new PendingRelease(changes, problems);
    }

    private static ReleaseProblem unresolved(DefinitionReference reference) {
        String message = "The " + reference.fromKind() + " '" + reference.fromKey() + "' refers to the "
                + reference.toKind() + " '" + reference.toKey() + "', which the release would not carry.";
        return reference.optional()
                ? ReleaseProblem.warning(reference.fromKind(), reference.fromKey(), UNRESOLVED_REFERENCE, message)
                : ReleaseProblem.error(reference.fromKind(), reference.fromKey(), UNRESOLVED_REFERENCE, message);
    }
}
