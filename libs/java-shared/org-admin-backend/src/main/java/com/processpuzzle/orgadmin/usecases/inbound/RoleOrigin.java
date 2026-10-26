package com.processpuzzle.orgadmin.usecases.inbound;

/** Who manages a role. Derived on every read, never stored; see {@link FindOrganizationRoles}. */
public enum RoleOrigin {
    /** {@code org-admin}, {@code org-member} and the identity provider's own roles. Nobody edits these here. */
    PLATFORM,
    /** A workflow role definition has this id; it is edited in the designer. */
    WORKFLOW,
    /** Defined by the organization's administrator, who may edit and delete it. */
    ADMIN
}
