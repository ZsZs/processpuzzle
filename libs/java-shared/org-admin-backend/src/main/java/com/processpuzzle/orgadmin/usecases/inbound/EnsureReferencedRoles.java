package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.tenancy.RolesReferenced;
import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.orgadmin.usecases.outbound.RoleProvisioningPort;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;

/**
 * The organization's role catalog: makes sure every role another feature refers to exists in the
 * realm, so a user can pick it on My Profile and the token can carry it.
 *
 * <p>Runs after the referencing transaction has committed, because creating a role is an HTTP call to
 * another system that cannot join it, and the reference is the fact — an unreachable Keycloak is no
 * reason to refuse an author their document. A failure is therefore logged and never propagated; the
 * next save of the same reference retries.
 *
 * <p>Names the platform or Keycloak own ({@code org-admin}, {@code org-member}, {@code platform-admin},
 * the built-ins, {@code default-roles-*}) are skipped: an author must not be able to mint authority by
 * typing a name. This runs without a request principal, so it does not go through
 * {@link TenantRealmResolver}, whose membership check belongs to the interactive screens.
 */
@Component
public class EnsureReferencedRoles {

    private static final Logger LOG = LoggerFactory.getLogger(EnsureReferencedRoles.class);
    private static final Set<String> RESERVED = Set.of("platform-admin", "offline_access", "uma_authorization");
    private static final String DEFAULT_ROLES_PREFIX = "default-roles-";

    private final RoleProvisioningPort provisioning;
    private final TenantRealmDirectory tenants;

    public EnsureReferencedRoles(RoleProvisioningPort provisioning, ObjectProvider<TenantRealmDirectory> tenants) {
        this.provisioning = provisioning;
        this.tenants = tenants.getIfUnique(() -> TenantRealmDirectory.BY_CONVENTION);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRolesReferenced(RolesReferenced event) {
        List<String> wanted = event.roles().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .filter(name -> !isReserved(name))
                .toList();
        if (wanted.isEmpty() || event.orgKey() == null) {
            return;
        }
        try {
            tenants.find(event.orgKey())
                    .filter(TenantRealmDirectory.Tenant::administerable)
                    .ifPresent(tenant -> provisioning.ensureRoles(tenant.realm(), wanted, event.description()));
        } catch (IdentityProviderUnavailableException | IllegalStateException ex) {
            LOG.warn("Could not create the referenced roles {} in organization '{}'; saving the reference "
                    + "again retries. {}", wanted, event.orgKey(), ex.getMessage());
        }
    }

    static boolean isReserved(String name) {
        return TenantRoles.ALL.contains(name) || RESERVED.contains(name) || name.startsWith(DEFAULT_ROLES_PREFIX);
    }
}
