package com.processpuzzle.workflow.definition.usecases.outbound;

import java.util.List;

/**
 * Stand-in used when no identity provider is configured.
 *
 * <p><b>It ignores writes and answers reads empty.</b> That is a deliberate divergence from
 * org-admin's {@code NoOpUserDirectoryPort}, which refuses writes with a 503, and the difference is
 * the whole design decision in this class. There, refusing is the honest answer because the
 * invitation <em>was</em> the operation: nothing had happened yet, and reporting success would
 * promise a user who will never exist. Here the operation has already committed — the role is in the
 * catalog, the request has been answered — and this port is only a projection of it. Throwing would
 * fail an after-commit listener over a deployment choice, and would take role authoring out of
 * service in every deployment that runs base-workflow without Keycloak, which the framework
 * explicitly supports.
 *
 * <p>Answering reads empty is consistent with that: with no identity provider there are no realm
 * roles, so the reconcile diff correctly finds nothing to compare against and, because it never
 * prunes, does nothing at all.
 *
 * <p>Deliberately not a {@code @Component}: {@code RoleDirectoryConfiguration} registers either this
 * or the Keycloak adapter, so a real adapter never competes with it for injection.
 */
public class NoOpRoleDirectoryPort implements RoleDirectoryPort {

    @Override
    public void upsertRole(String orgKey, String roleName, String description) {
        // Intentionally empty: see the class comment on why this is silence rather than a failure.
    }

    @Override
    public void deleteRole(String orgKey, String roleName) {
        // Intentionally empty: see the class comment on why this is silence rather than a failure.
    }

    @Override
    public List<String> findRoleNames(String orgKey) {
        return List.of();
    }
}
