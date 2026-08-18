package com.processpuzzle.workflow.execution.usecases.outbound;

/**
 * Development stand-in that permits every membership check, used when the deploying application
 * supplies no {@link RoleMembershipPort} bean. Mirrors base-app-backend's
 * {@code PermitAllOrganizationAccessPolicy} exactly, including the reasoning: this exists so the
 * feature is usable and testable before base-entity's role membership query is wired into the
 * host application, not as something to ship to a multi-tenant production deployment.
 *
 * <p>Deliberately not a {@code @Component}: it is instantiated as a fallback by
 * {@code AssignTaskUseCase} via {@code ObjectProvider#getIfUnique}, so a real port bean never has
 * to compete with it.
 */
public class PermitAllRoleMembershipPort implements RoleMembershipPort {

    @Override
    public boolean isMember(String orgKey, String userId, String entityRoleId) {
        return true;
    }
}
