package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.OrganizationReleaseRefusedException;
import com.processpuzzle.core.definition.OrganizationReleaser;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.starter.bundle.CommonKeys;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Upgrades a starter's common to a new version and releases it, at once (concept §5.4, step 2 of a rollout): a common's
 * members all read it, so there is no review stage in between and no organization to apply it later. Server-side only —
 * the platform's rollout decides, and the hosting application exposes it to that rollout and to nobody else.
 *
 * <p>The upgrade and the release are one transaction. A release that is refused — most importantly because members
 * still refer to something the new version takes away — rolls the upgrade back with it, so the common is never left with
 * unreleased drafts, and the refusal comes back with the report of what the upgrade would have done.
 */
@Service
public class UpgradeCommon {

    /** What a rollout of a common did. */
    public record Result(UpgradeReport upgrade, Release release, List<ReleaseProblem> problems) {

        public Result {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }

        /** Whether the common is now at the new version, released. */
        public boolean done() {
            return upgrade.status() == UpgradeReport.Status.APPLIED && release != Release.REFUSED;
        }
    }

    /**
     * RELEASED — the new drafts are live. NOTHING_PENDING — the upgrade changed no definition, so there was nothing to
     * release. REFUSED — the release was refused and everything was rolled back. NOT_ATTEMPTED — the upgrade itself was
     * refused.
     */
    public enum Release {
        RELEASED, NOTHING_PENDING, REFUSED, NOT_ATTEMPTED
    }

    private final UpgradeStarter upgrade;
    private final ObjectProvider<OrganizationReleaser> releaser;
    private final DataSpaceRepository dataSpaces;
    private final TransactionTemplate transactionTemplate;

    public UpgradeCommon(UpgradeStarter upgrade, ObjectProvider<OrganizationReleaser> releaser, DataSpaceRepository dataSpaces,
                         PlatformTransactionManager transactionManager) {
        this.upgrade = upgrade;
        this.releaser = releaser;
        this.dataSpaces = dataSpaces;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @param commonKey the common's key, which is also the id of its starter: {@code <starter>-common}
     * @throws IllegalArgumentException if {@code commonKey} is not a common
     * @throws IllegalStateException    if the host cannot release
     */
    public Result upgradeAndRelease(String commonKey, String version, String by) {
        if (!CommonKeys.isCommonId(commonKey) || !dataSpaces.existsById(commonKey)) {
            throw new IllegalArgumentException("'" + commonKey + "' is not a common.");
        }
        OrganizationReleaser release = releaser.getIfAvailable();
        if (release == null) {
            throw new IllegalStateException("This application cannot release, so it cannot roll out a common.");
        }
        UpgradeReport[] upgraded = new UpgradeReport[1];
        try {
            return transactionTemplate.execute(status -> {
                upgraded[0] = upgrade.provision(commonKey, commonKey, version, by);
                if (upgraded[0].status() != UpgradeReport.Status.APPLIED) {
                    status.setRollbackOnly();
                    return new Result(upgraded[0], Release.NOT_ATTEMPTED, List.of());
                }
                OrganizationReleaser.Outcome outcome = release.release(commonKey, by);
                return new Result(upgraded[0], outcome == OrganizationReleaser.Outcome.RELEASED ? Release.RELEASED : Release.NOTHING_PENDING, List.of());
            });
        } catch (OrganizationReleaseRefusedException refused) {
            UpgradeReport wouldHave = new UpgradeReport(false, UpgradeReport.Status.WOULD_APPLY, upgraded[0].starterId(),
                upgraded[0].fromVersion(), upgraded[0].toVersion(), upgraded[0].items(), upgraded[0].skippedKinds(), List.of(), upgraded[0].migrationSteps());
            return new Result(wouldHave, Release.REFUSED, refused.problems());
        }
    }
}
