package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converges the identity provider's realm roles onto the organization's role catalog.
 *
 * <p>All the policy of the projection lives here, so that neither
 * {@code RoleDirectorySyncListener} (which only knows when to run) nor
 * {@code KeycloakRoleDirectoryAdapter} (which only knows how to talk to Keycloak) carries any of it.
 * There are three rules and each is load-bearing.
 *
 * <h2>Failure is logged, never propagated</h2>
 *
 * <p>Every entry point here runs after the writing transaction has committed and the request has been
 * answered. An {@link IdentityProviderUnavailableException} at that point cannot be reported to
 * anyone and cannot undo anything, so it is logged at WARN naming the organization and the role, and
 * the projection is left to {@link #reconcile()} on the next restart. Letting it escape would only
 * fill the log with stack traces from Spring's event infrastructure.
 *
 * <h2>Reserved names are skipped</h2>
 *
 * <p>{@link #RESERVED_ROLE_IDS} and the {@link #DEFAULT_ROLES_PREFIX} name realm roles the platform
 * or Keycloak itself interprets. {@code CreateRoleDefinitionUseCase} refuses them at authoring time,
 * which is where an author gets a usable error; the check is repeated here as defence for rows that
 * predate that guard, and for the import path.
 *
 * <h2>Reconcile creates, and never removes</h2>
 *
 * <p>See {@link #reconcile()}.
 */
@Component
public class SyncRoleDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(SyncRoleDirectory.class);

    /**
     * Realm role names a role definition may not claim.
     *
     * <p>{@code org-admin} and {@code org-member} are the platform's own, granted and revoked by
     * org-admin and read out of tokens by access policies; {@code offline_access} and
     * {@code uma_authorization} are Keycloak's, created with every realm. A definition minting any of
     * them would let a tenant grant itself platform authority by authoring a row, and deleting the
     * definition again would revoke a role the realm needs.
     */
    public static final Set<String> RESERVED_ROLE_IDS =
            Set.of(TenantRoles.ORG_ADMIN, TenantRoles.ORG_MEMBER, "offline_access", "uma_authorization");

    /**
     * Prefix of Keycloak's per-realm composite default role ({@code default-roles-<realm>}), which
     * every new user is granted. Matched as a prefix because the realm name is part of it.
     */
    public static final String DEFAULT_ROLES_PREFIX = "default-roles-";

    private final RoleDefinitionRepository repository;
    private final RoleDirectoryPort directory;

    public SyncRoleDirectory(RoleDefinitionRepository repository, RoleDirectoryPort directory) {
        this.repository = repository;
        this.directory = directory;
    }

    /** Whether {@code roleId} names a realm role the platform or Keycloak owns. */
    public static boolean isReserved(String roleId) {
        return roleId != null
                && (RESERVED_ROLE_IDS.contains(roleId) || roleId.startsWith(DEFAULT_ROLES_PREFIX));
    }

    /** Creates or updates the realm role matching a definition that has just been written. */
    public void sync(String orgKey, String roleId, String name, String description) {
        if (isReserved(roleId)) {
            LOG.warn("Role definition '{}' in organization '{}' names a reserved realm role; "
                    + "not projected into the identity provider.", roleId, orgKey);
            return;
        }
        try {
            directory.upsertRole(orgKey, roleId, describe(name, description));
        } catch (IdentityProviderUnavailableException ex) {
            LOG.warn("Could not project role '{}' of organization '{}' into the identity provider; "
                    + "the next startup reconcile will retry. {}", roleId, orgKey, ex.getMessage());
        }
    }

    /** Removes the realm role matching a definition that has just been deleted. */
    public void remove(String orgKey, String roleId) {
        if (isReserved(roleId)) {
            LOG.warn("Role definition '{}' in organization '{}' names a reserved realm role; "
                    + "leaving the realm role in place.", roleId, orgKey);
            return;
        }
        try {
            directory.deleteRole(orgKey, roleId);
        } catch (IdentityProviderUnavailableException ex) {
            LOG.warn("Could not remove role '{}' of organization '{}' from the identity provider; "
                    + "the realm role is now orphaned and has to be removed by hand. {}",
                    roleId, orgKey, ex.getMessage());
        }
    }

    /**
     * Heals drift between the catalog and the realms: one read per organization, then a create for
     * every definition the realm does not declare.
     *
     * <p><b>It never deletes.</b> A realm's roles are one namespace shared with {@code org-admin},
     * {@code org-member}, {@code default-roles-<realm>}, {@code offline_access} and whatever an
     * administrator made by hand — a reconcile that pruned everything it found no definition for
     * would break the realm on its first run. Removal happens only where intent is known, which is
     * {@link #remove}.
     *
     * <p>Nor does it rewrite a role it does find. Descriptions are converged by the authoring path;
     * the diff is over names alone, which is why {@link RoleDirectoryPort#findRoleNames} projects
     * names alone.
     *
     * <p>Best-effort per organization: one unreachable realm is logged and the rest are still
     * reconciled.
     */
    public void reconcile() {
        Map<String, List<RoleDefinition>> byOrganization = repository.findAll().stream()
                .filter(role -> role.getOrgKey() != null && role.getId() != null)
                .collect(Collectors.groupingBy(RoleDefinition::getOrgKey));

        for (Map.Entry<String, List<RoleDefinition>> organization : byOrganization.entrySet()) {
            reconcile(organization.getKey(), organization.getValue());
        }
    }

    private void reconcile(String orgKey, List<RoleDefinition> definitions) {
        Set<String> declared;
        try {
            declared = new HashSet<>(directory.findRoleNames(orgKey));
        } catch (IdentityProviderUnavailableException ex) {
            LOG.warn("Could not read the realm roles of organization '{}'; its {} role definition(s) "
                    + "were not reconciled. {}", orgKey, definitions.size(), ex.getMessage());
            return;
        }

        int created = 0;
        for (RoleDefinition definition : definitions) {
            if (isReserved(definition.getId()) || declared.contains(definition.getId())) {
                continue;
            }
            sync(orgKey, definition.getId(), definition.getName(), definition.getDescription());
            created++;
        }
        if (created > 0) {
            LOG.info("Reconciled organization '{}': {} of {} role definition(s) were missing from its "
                    + "realm and have been created.", orgKey, created, definitions.size());
        }
    }

    /**
     * What the realm role's description says. The definition's own description when it has one,
     * otherwise its display name — a realm role listed in the Keycloak console with no description at
     * all is harder to place than one echoing the name an author gave it.
     */
    private static String describe(String name, String description) {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return name != null && !name.isBlank() ? name : null;
    }
}
