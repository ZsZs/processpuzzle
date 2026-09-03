package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.platformadmin.api.OrganizationsApi;
import com.processpuzzle.platformadmin.model.KeyAvailability;
import com.processpuzzle.platformadmin.model.Organization;
import com.processpuzzle.platformadmin.model.OrganizationInput;
import com.processpuzzle.platformadmin.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.usecase.CheckOrganizationKey;
import com.processpuzzle.platformadmin.usecase.DeleteOrganization;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.platformadmin.usecase.ProvisionOrganization;
import com.processpuzzle.platformadmin.usecase.UpdateOrganization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the tenant-facing half of the organization aggregate: sign-up, key availability,
 * and read/update/delete of one's own organization. Served at {@code /organizations*}, without the
 * {@code /platform} prefix, because these are not staff operations — {@code provisionOrganization} is
 * the public sign-up entry point and the other three are gated per tenant.
 *
 * <p><b>Why this class exists.</b> These five operations were declared in {@code base-app-api.yaml}
 * and served by {@code AppEndpoint}, long after the {@code Organization} aggregate itself had moved
 * here. The contract had simply not followed the code. base-app was therefore compiled against
 * platform-admin's use cases, domain and exception types purely to keep answering a URL that was no
 * longer about applications — and, as a result, could not be deployed without platform-admin at all.
 * Ownership now runs the whole way: aggregate, use cases, contract and controller.
 *
 * <p>One controller per generated tag interface, as elsewhere in this repository. That is not merely
 * convention here: {@link OrganizationsApi} and {@code PlatformOrganizationsApi} both declare a
 * {@code default getRequest()}, so a single class implementing both would not compile without
 * overriding it.
 *
 * <p>Gating lives in the use cases rather than in this class, matching
 * {@code PlatformOrganizationEndpoint} — each calls the guard before touching a repository, so a use
 * case is safe to invoke from anywhere and not only from behind this controller.
 */
@RestController
@LogClass
public class OrganizationEndpoint implements OrganizationsApi {

    private final ProvisionOrganization provisionOrganization;
    private final CheckOrganizationKey checkOrganizationKey;
    private final FindOrganization findOrganization;
    private final UpdateOrganization updateOrganization;
    private final DeleteOrganization deleteOrganization;
    private final PlatformAdminMapper mapper;

    public OrganizationEndpoint(ProvisionOrganization provisionOrganization,
                                CheckOrganizationKey checkOrganizationKey,
                                FindOrganization findOrganization,
                                UpdateOrganization updateOrganization,
                                DeleteOrganization deleteOrganization,
                                PlatformAdminMapper mapper) {
        this.provisionOrganization = provisionOrganization;
        this.checkOrganizationKey = checkOrganizationKey;
        this.findOrganization = findOrganization;
        this.updateOrganization = updateOrganization;
        this.deleteOrganization = deleteOrganization;
        this.mapper = mapper;
    }

    /**
     * Returns the organization alone. The operation it replaces answered with a
     * {@code ProvisioningResult} carrying the starter {@code AppDefinition} as well, written in the
     * same transaction by a base-app use case. base-app now creates that definition in a
     * {@code BEFORE_COMMIT} listener on {@code OrganizationProvisionedEvent}, so it is still
     * committed atomically with the tenant and still exists by the time this responds — but its
     * shape is base-app's business and does not belong in this contract.
     */
    @Override
    public ResponseEntity<Organization> provisionOrganization(OrganizationInput input) {
        return new ResponseEntity<>(mapper.toModel(provisionOrganization.execute(
                input.getKey(), mapper.toDetails(input))), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<KeyAvailability> checkOrganizationKey(String key) {
        return ResponseEntity.ok(mapper.toModel(checkOrganizationKey.execute(key)));
    }

    @Override
    public ResponseEntity<Organization> getOrganization(String orgKey) {
        return ResponseEntity.ok(mapper.toModel(findOrganization.execute(orgKey)));
    }

    @Override
    public ResponseEntity<Organization> updateOrganization(String orgKey, OrganizationUpdate input) {
        return ResponseEntity.ok(mapper.toModel(
                updateOrganization.execute(orgKey, mapper.toDetails(input))));
    }

    @Override
    public ResponseEntity<Void> deleteOrganization(String orgKey) {
        deleteOrganization.execute(orgKey);
        return ResponseEntity.noContent().build();
    }
}
