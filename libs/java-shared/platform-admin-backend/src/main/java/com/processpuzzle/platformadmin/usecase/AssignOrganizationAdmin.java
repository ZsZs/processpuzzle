package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationStatusConflictException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates an administrator in a tenant's own realm and grants it {@code org-admin} — the role that
 * lets it reach the org-admin API and administer everybody else in the tenant.
 *
 * <p>Reads in a transaction and then writes nothing here: the user lives in Keycloak, which is the
 * system of record for users, so there is no row to keep consistent. The read only confirms that the
 * tenant exists and is in a state where a realm exists to create the user in — a
 * {@code PROVISIONING} tenant has none yet, so without the check the call would fail deep inside
 * Keycloak with a message about a missing realm rather than about the tenant.
 *
 * <p>This is a platform operation rather than an org-admin one because of the case that motivated
 * it: a tenant that has locked itself out has no {@code org-admin} left to invite a replacement, so
 * somebody outside the tenant must be able to. It is not technically restricted to the <em>first</em>
 * administrator, and the name reflects the intent rather than a constraint.
 */
@Service
public class AssignOrganizationAdmin {

    private final OrganizationRepository repository;
    private final OrganizationGuard guard;
    private final IdentityRealmPort realms;

    public AssignOrganizationAdmin(OrganizationRepository repository, OrganizationGuard guard,
                                   IdentityRealmPort realms) {
        this.repository = repository;
        this.guard = guard;
        this.realms = realms;
    }

    @Transactional(readOnly = true)
    public Result execute(String orgKey, IdentityRealmPort.NewUser user) {
        guard.requirePlatformAdmin();
        Organization organization = repository.findById(orgKey)
                .orElseThrow(() -> new OrganizationNotFoundException(orgKey));

        if (organization.getStatus() == OrganizationStatus.PROVISIONING) {
            throw new OrganizationStatusConflictException(orgKey, organization.getStatus(),
                    "assign an administrator before its realm exists");
        }

        List<String> roles = List.of(IdentityRealmPort.ORG_ADMIN_ROLE, IdentityRealmPort.ORG_MEMBER_ROLE);
        String userId = realms.createAdminUser(orgKey, user, roles);

        return new Result(userId, orgKey, user, roles);
    }

    /**
     * The created administrator.
     *
     * @param userId the identity provider's own opaque id, not a ProcessPuzzle id
     * @param realm the realm the user was created in; always the tenant's {@code orgKey}
     * @param user what was requested, echoed back so the adapter needs no second read
     * @param roles the realm roles granted
     */
    public record Result(String userId, String realm, IdentityRealmPort.NewUser user, List<String> roles) {
    }
}
