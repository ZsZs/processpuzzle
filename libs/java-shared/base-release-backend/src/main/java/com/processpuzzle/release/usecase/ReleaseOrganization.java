package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.release.domain.OrgRelease;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Releases an organization: every pending definition of every kind, or nothing.
 *
 * <p>Plans again inside its own transaction rather than trusting a dry run the designer looked at, so
 * what is checked is what is released. Refuses while the plan has errors, while it has warnings the
 * caller has not confirmed, and while nothing is pending. Otherwise every participant releases its
 * kind in order, all in the one transaction — the platform runs on a single datasource — and the
 * release is recorded with the hash of each definition it carried. A participant that fails rolls
 * every kind back.
 */
@Service
public class ReleaseOrganization {

    private final OrganizationGuard guard;
    private final ReleasePlanner planner;
    private final ReleaseParticipants participants;
    private final OrgReleaseRepository releaseRepository;
    private final OrgReleaseItemRepository itemRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ReleaseOrganization(OrganizationGuard guard,
                        ReleasePlanner planner,
                        ReleaseParticipants participants,
                        OrgReleaseRepository releaseRepository,
                        OrgReleaseItemRepository itemRepository,
                        PlatformTransactionManager transactionManager,
                        ObjectProvider<Clock> clock) {
        this.guard = guard;
        this.planner = planner;
        this.participants = participants;
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /**
     * @param acknowledgeWarnings whether the caller has seen the plan's warnings and releases regardless
     * @param releasedBy          who is releasing, recorded with the release; may be null
     * @throws ReleaseRefusedException if the release was refused; nothing was released then
     */
    public ReleaseRecord execute(String orgKey, boolean acknowledgeWarnings, String releasedBy) {
        guard.requireDesign(orgKey);
        return releaseAuthorized(orgKey, acknowledgeWarnings, releasedBy);
    }

    /**
     * The release without the design-rights check, for a provisioning call that installs a vetted
     * starter on an organization's behalf, and therefore confirms its warnings.
     */
    public ReleaseRecord provision(String orgKey, String releasedBy) {
        return releaseAuthorized(orgKey, true, releasedBy);
    }

    private ReleaseRecord releaseAuthorized(String orgKey, boolean acknowledgeWarnings, String releasedBy) {
        return transactionTemplate.execute(status -> {
            PendingRelease pending = planner.plan(orgKey);
            if (pending.changes().isEmpty()) {
                throw new ReleaseRefusedException(orgKey, pending, ReleaseRefusedException.Reason.NOTHING_PENDING);
            }
            if (pending.hasErrors()) {
                throw new ReleaseRefusedException(orgKey, pending, ReleaseRefusedException.Reason.ERRORS);
            }
            if (pending.hasWarnings() && !acknowledgeWarnings) {
                throw new ReleaseRefusedException(orgKey, pending, ReleaseRefusedException.Reason.UNCONFIRMED_WARNINGS);
            }

            for (ReleaseParticipant participant : participants.inOrder()) {
                participant.release(orgKey);
            }
            return recordRelease(orgKey, pending.changes(), releasedBy);
        });
    }

    private ReleaseRecord recordRelease(String orgKey, List<PendingChange> changes, String releasedBy) {
        long number = releaseRepository.findFirstByOrgKeyOrderByReleaseNumberDesc(orgKey)
                .map(OrgRelease::getReleaseNumber)
                .orElse(0L) + 1;
        OrgRelease release = releaseRepository.save(new OrgRelease(orgKey, number, clock.instant(), releasedBy));
        List<OrgReleaseItem> items = itemRepository.saveAll(IntStream.range(0, changes.size())
                .mapToObj(index -> {
                    PendingChange change = changes.get(index);
                    return new OrgReleaseItem(orgKey, number, index, change.kind(), change.key(), change.change(),
                            change.fingerprint() == null ? null : sha256(change.fingerprint()));
                })
                .toList());
        return new ReleaseRecord(release, items);
    }

    static String sha256(String fingerprint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
