package com.processpuzzle.core.tenancy;

import java.util.List;
import java.util.Map;

/**
 * What one feature knows about the roles its data names, so that org-admin can tell an administrator
 * whether a role is still in use without depending on the features that use it.
 *
 * <p>Many implementations, collected by org-admin — the same shape as {@code ReleaseParticipant}. A
 * feature contributes by declaring a bean; org-admin does not know which features exist, and a
 * deployment without a feature simply contributes nothing.
 *
 * <p>Answers are read live, one query per kind, because they are asked for an administrative action:
 * deleting a role, or listing the catalog with each role's origin. Not for a hot path.
 */
public interface RoleReferenceContributor {

    /**
     * Every role this feature names in {@code orgKey}, with what names it.
     *
     * @return role name to references; a role no object names is simply absent
     */
    Map<String, List<RoleReference>> referencedRoles(String orgKey);
}
