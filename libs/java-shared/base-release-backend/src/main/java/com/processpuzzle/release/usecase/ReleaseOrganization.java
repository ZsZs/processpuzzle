package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgRelease;
import com.processpuzzle.release.domain.OrgReleaseContent;
import com.processpuzzle.release.domain.OrgReleaseContentRepository;
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
 * <p>Runs the pending migration steps first, then plans again inside its own transaction rather than
 * trusting a dry run the designer looked at, so what is checked — the data as the steps left it — is what
 * is released. Refuses while the plan has errors, while it has warnings the
 * caller has not confirmed, and while nothing is pending. Otherwise every participant releases its
 * kind in order, all in the one transaction — the platform runs on a single datasource — and the
 * release is recorded with the hash and the content of each definition it carried — see
 * {@link OrgReleaseContent} — and with the steps it ran. A participant that fails rolls every kind back,
 * and every step with it.
 */
@Service
public class ReleaseOrganization {

    private final OrganizationGuard guard;
    private final MigratingPlanner planner;
    private final ReleaseParticipants participants;
    private final OrgReleaseRepository releaseRepository;
    private final OrgReleaseItemRepository itemRepository;
    private final OrgReleaseContentRepository contentRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ReleaseOrganization(OrganizationGuard guard,
                        MigratingPlanner planner,
                        ReleaseParticipants participants,
                        OrgReleaseRepository releaseRepository,
                        OrgReleaseItemRepository itemRepository,
                        OrgReleaseContentRepository contentRepository,
                        PlatformTransactionManager transactionManager,
                        ObjectProvider<Clock> clock) {
        this.guard = guard;
        this.planner = planner;
        this.participants = participants;
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
        this.contentRepository = contentRepository;
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
            MigratingPlanner.Planned planned = planner.plan(orgKey);
            PendingRelease pending = planned.pending();
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
            ReleaseRecord released = recordRelease(orgKey, pending.changes(), releasedBy);
            return new ReleaseRecord(released.release(), released.items(),
                    recordSteps(released.release().getReleaseNumber(), planned.steps(), pending.steps()));
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
        contentRepository.saveAll(IntStream.range(0, changes.size())
                .filter(index -> changes.get(index).fingerprint() != null)
                .mapToObj(index -> new OrgReleaseContent(items.get(index).getId(), orgKey, changes.get(index).fingerprint()))
                .toList());
        return new ReleaseRecord(release, items);
    }

    /** The steps the release ran, each with what it did; they are no longer pending. */
    private static List<OrgMigrationStep> recordSteps(long number, List<OrgMigrationStep> steps, List<StepOutcome> outcomes) {
        for (int index = 0; index < steps.size(); index++) {
            StepOutcome outcome = outcomes.get(index);
            steps.get(index).releasedIn(number,
                    outcome.status() == StepOutcome.Status.APPLIED ? OrgMigrationStep.Outcome.APPLIED : OrgMigrationStep.Outcome.SKIPPED,
                    outcome.affectedObjects(), outcome.reason());
        }
        return steps;
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
