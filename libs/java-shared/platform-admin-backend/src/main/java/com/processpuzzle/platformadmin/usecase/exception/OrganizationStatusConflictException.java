package com.processpuzzle.platformadmin.usecase.exception;

import com.processpuzzle.platformadmin.domain.OrganizationStatus;

/**
 * The requested lifecycle transition is not available from the tenant's current status. Surfaced as
 * 409.
 *
 * <p>Raised for one case only in practice: activating a {@code PROVISIONING} organization, whose
 * realm does not exist yet, so there is nothing to enable. Suspending an already-suspended tenant is
 * deliberately <em>not</em> a conflict — it is a retry of something that already holds.
 */
public class OrganizationStatusConflictException extends RuntimeException {

    public OrganizationStatusConflictException(String orgKey, OrganizationStatus current, String attempted) {
        super("Organization '" + orgKey + "' is " + current + "; cannot " + attempted + ".");
    }
}
