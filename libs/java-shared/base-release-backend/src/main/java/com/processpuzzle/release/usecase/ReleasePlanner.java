package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    static final String MEMBER_REFERENCES = "release.common-member-references";

    private final ReleaseParticipants participants;
    private final ObjectProvider<DataSpaceDirectory> dataSpaces;

    @Autowired
    ReleasePlanner(ReleaseParticipants participants, ObjectProvider<DataSpaceDirectory> dataSpaces) {
        this.participants = participants;
        this.dataSpaces = dataSpaces;
    }

    /** A planner of a host without commons. */
    ReleasePlanner(ReleaseParticipants participants) {
        this(participants, null);
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
        problems.addAll(memberReferenceProblems(orgKey));
        return new PendingRelease(changes, problems);
    }

    /**
     * A common must not release away what its members still refer to: its shared types are theirs to refer to, and a
     * member's definitions are not the common's to break. For each key the release would take away, the members that
     * still refer to it are named; removing or renaming it waits until they stop. Looked up only when something is
     * taken away, since it reads every member.
     */
    private List<ReleaseProblem> memberReferenceProblems(String orgKey) {
        DataSpaceDirectory directory = dataSpaces == null ? null : dataSpaces.getIfAvailable();
        if (directory == null || !directory.isCommon(orgKey)) {
            return List.of();
        }
        Map<String, Set<String>> removed = removedKeysByKind(orgKey);
        if (removed.isEmpty()) {
            return List.of();
        }
        List<ReleaseProblem> problems = new ArrayList<>();
        referrers(directory.membersOf(orgKey), removed).forEach((taken, who) ->
            problems.add(ReleaseProblem.error(taken.kind(), taken.key(), MEMBER_REFERENCES, "The " + taken.kind() + " '" + taken.key()
                + "' is referred to by members of this common: " + String.join(", ", who)
                + ". It can be removed or renamed once they no longer refer to it.")));
        return problems;
    }

    private Map<String, Set<String>> removedKeysByKind(String orgKey) {
        Map<String, Set<String>> removed = new HashMap<>();
        participants.inOrder().forEach(participant -> {
            Set<String> keys = participant.removedReferableKeys(orgKey);
            if (!keys.isEmpty()) {
                removed.put(participant.kind(), keys);
            }
        });
        return removed;
    }

    /** Who refers to each removed key: the member, and the definition of it that does. */
    private Map<TakenAway, List<String>> referrers(Set<String> members, Map<String, Set<String>> removed) {
        Map<TakenAway, List<String>> referrers = new LinkedHashMap<>();
        for (String member : members) {
            for (ReleaseParticipant participant : participants.inOrder()) {
                participant.references(member).stream()
                    .filter(reference -> removed.getOrDefault(reference.toKind(), Set.of()).contains(reference.toKey()))
                    .forEach(reference -> referrers.computeIfAbsent(new TakenAway(reference.toKind(), reference.toKey()), key -> new ArrayList<>())
                        .add(member + " (" + reference.fromKind() + " '" + reference.fromKey() + "')"));
            }
        }
        return referrers;
    }

    /** A definition of the common that the release takes away, or renames. */
    private record TakenAway(String kind, String key) {
    }

    private static ReleaseProblem unresolved(DefinitionReference reference) {
        String message = "The " + reference.fromKind() + " '" + reference.fromKey() + "' refers to the "
                + reference.toKind() + " '" + reference.toKey() + "', which the release would not carry.";
        return reference.optional()
                ? ReleaseProblem.warning(reference.fromKind(), reference.fromKey(), UNRESOLVED_REFERENCE, message)
                : ReleaseProblem.error(reference.fromKind(), reference.fromKey(), UNRESOLVED_REFERENCE, message);
    }
}
