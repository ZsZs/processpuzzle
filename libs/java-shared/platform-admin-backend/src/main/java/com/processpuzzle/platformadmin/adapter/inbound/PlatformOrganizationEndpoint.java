package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.platformadmin.api.PlatformOrganizationsApi;
import com.processpuzzle.platformadmin.model.AdminUser;
import com.processpuzzle.platformadmin.model.AdminUserInput;
import com.processpuzzle.platformadmin.model.Organization;
import com.processpuzzle.platformadmin.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.model.PageOfOrganization;
import com.processpuzzle.platformadmin.usecase.ActivateOrganization;
import com.processpuzzle.platformadmin.usecase.AssignOrganizationAdmin;
import com.processpuzzle.platformadmin.usecase.DeleteOrganization;
import com.processpuzzle.platformadmin.usecase.FindAllOrganizations;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import com.processpuzzle.platformadmin.usecase.SuspendOrganization;
import com.processpuzzle.platformadmin.usecase.UpdateOrganization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the organization half of the {@code /platform/**} staff surface. Holds no logic of
 * its own: it delegates to a use case and maps the result.
 *
 * <p>One controller per generated tag interface, as everywhere else in this repository — and here it
 * is not merely convention: the two interfaces both declare a {@code default getRequest()}, so a
 * single class implementing both does not compile without overriding it.
 *
 * <p>Every operation is gated on the {@code platform-admin} authority, and the gate lives in the use
 * cases rather than here — each calls {@link OrganizationGuard#requirePlatformAdmin()} before
 * touching a repository. Putting it in this class instead would leave the use cases individually
 * unsafe to call from anywhere else, which for operations that delete tenants is not worth one fewer
 * line each.
 */
@RestController
@LogClass
public class PlatformOrganizationEndpoint implements PlatformOrganizationsApi {

    private final FindAllOrganizations findAllOrganizations;
    private final FindOrganization findOrganization;
    private final UpdateOrganization updateOrganization;
    private final DeleteOrganization deleteOrganization;
    private final SuspendOrganization suspendOrganization;
    private final ActivateOrganization activateOrganization;
    private final AssignOrganizationAdmin assignOrganizationAdmin;
    private final OrganizationGuard guard;
    private final PlatformAdminMapper mapper;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public PlatformOrganizationEndpoint(FindAllOrganizations findAllOrganizations,
                                        FindOrganization findOrganization,
                                        UpdateOrganization updateOrganization,
                                        DeleteOrganization deleteOrganization,
                                        SuspendOrganization suspendOrganization,
                                        ActivateOrganization activateOrganization,
                                        AssignOrganizationAdmin assignOrganizationAdmin,
                                        OrganizationGuard guard,
                                        PlatformAdminMapper mapper) {
        this.findAllOrganizations = findAllOrganizations;
        this.findOrganization = findOrganization;
        this.updateOrganization = updateOrganization;
        this.deleteOrganization = deleteOrganization;
        this.suspendOrganization = suspendOrganization;
        this.activateOrganization = activateOrganization;
        this.assignOrganizationAdmin = assignOrganizationAdmin;
        this.guard = guard;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PageOfOrganization> listOrganizations(String where, String order,
                                                                Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toOrganizationPage(
                findAllOrganizations.execute(where, order, page, size)));
    }

    /**
     * Reads through the unguarded variant on purpose: {@code FindOrganization.execute} checks tenant
     * membership, which platform staff do not have. The staff authority is checked here instead.
     */
    @Override
    public ResponseEntity<Organization> getOrganizationAsPlatformAdmin(String orgKey) {
        guard.requirePlatformAdmin();
        return ResponseEntity.ok(mapper.toModel(findOrganization.executeUnguarded(orgKey)));
    }

    @Override
    public ResponseEntity<Organization> updateOrganizationAsPlatformAdmin(String orgKey,
                                                                          OrganizationUpdate input) {
        return ResponseEntity.ok(mapper.toModel(
                updateOrganization.executeAsPlatformAdmin(orgKey, mapper.toDetails(input))));
    }

    @Override
    public ResponseEntity<Void> deleteOrganizationAsPlatformAdmin(String orgKey) {
        deleteOrganization.executeAsPlatformAdmin(orgKey);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Organization> suspendOrganization(String orgKey) {
        return ResponseEntity.ok(mapper.toModel(suspendOrganization.execute(orgKey)));
    }

    @Override
    public ResponseEntity<Organization> activateOrganization(String orgKey) {
        return ResponseEntity.ok(mapper.toModel(activateOrganization.execute(orgKey)));
    }

    @Override
    public ResponseEntity<AdminUser> assignOrganizationAdmin(String orgKey, AdminUserInput input) {
        AssignOrganizationAdmin.Result result =
                assignOrganizationAdmin.execute(orgKey, mapper.toNewUser(input));
        return new ResponseEntity<>(mapper.toModel(result), HttpStatus.CREATED);
    }
}
