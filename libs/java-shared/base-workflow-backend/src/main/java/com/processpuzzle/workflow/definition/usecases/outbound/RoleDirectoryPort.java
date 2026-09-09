package com.processpuzzle.workflow.definition.usecases.outbound;

import java.util.List;

/**
 * The identity provider's copy of an organization's roles — the one outbound write base-workflow
 * owns.
 *
 * <p>A task instance is performed by a <em>role</em> ({@code TaskUse.performedBy}), and "which tasks
 * are mine" is only answerable if the roles a user holds are carried by their token. That makes the
 * catalog's roles something the identity provider has to know about too, so every authoring write is
 * projected here. The projection is <b>one way</b>: the catalog is the system of record and this port
 * is written to, never read from for authoring purposes. {@link #findRoleNames} exists solely so
 * {@code SyncRoleDirectory} can diff and heal drift.
 *
 * <h2>Realm name equals organization key</h2>
 *
 * <p>The adapters in this library resolve the realm from {@code orgKey} directly, which is the
 * platform's own naming rule — realm name, organization key and bucket prefix are one string, as
 * {@code org-admin}'s {@code TenantRealmDirectory.BY_CONVENTION} also states. base-workflow cannot
 * depend on org-admin to ask, so it applies the same convention itself. A deployment that owns a
 * tenant registry, and therefore may name realms differently, substitutes its own implementation of
 * this port and resolves the realm from that registry.
 *
 * <h2>Every operation is idempotent</h2>
 *
 * <p>Writes are projections of an already-committed fact, so they will be retried — by the next
 * authoring write, or by the startup reconcile. Upserting a role that exists and deleting one that
 * does not are both successes.
 */
public interface RoleDirectoryPort {

    /**
     * Creates the role in {@code orgKey}'s realm, or updates its description when it already exists.
     *
     * @param roleName    the realm role's name — {@code RoleDefinition.id} verbatim
     * @param description human-readable description, or {@code null} to leave it unset
     */
    void upsertRole(String orgKey, String roleName, String description);

    /** Removes the role from {@code orgKey}'s realm; absent is success. */
    void deleteRole(String orgKey, String roleName);

    /**
     * Names of every realm role in {@code orgKey}'s realm — including the ones no definition
     * produced, such as {@code org-admin} or {@code default-roles-<realm>}.
     *
     * <p>Names only, because names are the whole of what the reconcile diff decides from: it creates
     * what is missing and, by design, never removes or rewrites what it finds (see
     * {@code SyncRoleDirectory#reconcile}).
     */
    List<String> findRoleNames(String orgKey);
}
