package com.processpuzzle.release.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The dry run of a release: what it would carry, and what would refuse or qualify it. Writes nothing. */
@Service
@Transactional(readOnly = true)
public class DescribePendingRelease {

    private final OrganizationGuard guard;
    private final ReleasePlanner planner;

    DescribePendingRelease(OrganizationGuard guard, ReleasePlanner planner) {
        this.guard = guard;
        this.planner = planner;
    }

    public PendingRelease execute(String orgKey) {
        guard.requireDesign(orgKey);
        return planner.plan(orgKey);
    }
}
