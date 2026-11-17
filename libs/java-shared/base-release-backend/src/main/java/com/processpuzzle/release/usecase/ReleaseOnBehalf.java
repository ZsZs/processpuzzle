package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.OrganizationReleaser;
import com.processpuzzle.core.definition.OrganizationReleaseRefusedException;
import org.springframework.stereotype.Component;

/**
 * {@link OrganizationReleaser} over the release use case, without a principal's design rights: for code that wrote the
 * drafts itself. It first asks the participants whether anything is pending, because the release use case refuses an
 * empty release by throwing — which, inside the caller's transaction, would roll that back although nothing is wrong.
 */
@Component
public class ReleaseOnBehalf implements OrganizationReleaser {

    private final ReleaseOrganization release;
    private final ReleaseParticipants participants;

    ReleaseOnBehalf(ReleaseOrganization release, ReleaseParticipants participants) {
        this.release = release;
        this.participants = participants;
    }

    @Override
    public Outcome release(String orgKey, String releasedBy) {
        boolean pending = participants.inOrder().stream().map(participant -> participant.pending(orgKey))
            .anyMatch(changes -> !changes.isEmpty());
        if (!pending) {
            return Outcome.NOTHING_PENDING;
        }
        try {
            release.provision(orgKey, releasedBy);
            return Outcome.RELEASED;
        } catch (ReleaseRefusedException refused) {
            throw new OrganizationReleaseRefusedException(refused.getMessage(), refused.getPending().problems());
        }
    }
}
