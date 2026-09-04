package com.processpuzzle.workflow.execution.usecases.outbound;

/**
 * Checks whether a user holds a given base-entity role. base-workflow never depends on
 * base-entity directly — it exposes no named interface for this module to reach into (unlike
 * base-rule and base-state) — so, mirroring base-app-backend's {@code OrganizationAccessPolicy}
 * pattern, the host application (processpuzzle-testbed-backend) is expected to provide a {@code @Component}
 * bean implementing this port, typically by calling into base-entity's own API.
 *
 * <p>Used by {@code AssignTaskUseCase} when the target {@code RoleDefinition.entityRoleId} is
 * set; assignment to a role with no {@code entityRoleId} configured is always allowed (there is
 * nothing to check membership against).
 */
public interface RoleMembershipPort {

    boolean isMember(String orgKey, String userId, String entityRoleId);
}
