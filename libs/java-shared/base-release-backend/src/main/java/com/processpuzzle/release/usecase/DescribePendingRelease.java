package com.processpuzzle.release.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The dry run of a release: what it would carry, and what would refuse or qualify it. Runs the pending migration
 * steps, so that the checks judge the data as the release would find it, and rolls their writes back.
 */
@Service
public class DescribePendingRelease {

    private final OrganizationGuard guard;
    private final MigratingPlanner planner;
    private final TransactionTemplate transactionTemplate;

    DescribePendingRelease(OrganizationGuard guard, MigratingPlanner planner, PlatformTransactionManager transactionManager) {
        this.guard = guard;
        this.planner = planner;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PendingRelease execute(String orgKey) {
        guard.requireDesign(orgKey);
        return transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return planner.plan(orgKey).pending();
        });
    }
}
