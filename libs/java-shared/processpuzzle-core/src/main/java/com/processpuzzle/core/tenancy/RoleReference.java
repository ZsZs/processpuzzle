package com.processpuzzle.core.tenancy;

/**
 * One place that names a role: a document's reader roles, a navigation item's visibility, a workflow
 * role definition.
 *
 * @param feature the feature that holds the reference, for example {@code document}
 * @param kind    what kind of thing names the role; {@link #ROLE_DEFINITION} is special, see there
 * @param id      the referring object's id
 * @param title   what to call it to an administrator; may be {@code null}
 */
public record RoleReference(String feature, String kind, String id, String title) {

    /**
     * The role is the id of a workflow role definition. Such a role is projected from the designer and
     * edited there, which is what makes its origin {@code workflow} rather than {@code admin}.
     */
    public static final String ROLE_DEFINITION = "role-definition";

    public boolean isRoleDefinition() {
        return ROLE_DEFINITION.equals(kind);
    }
}
