package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvalidRoleNameException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleInUseException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleNotFoundException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleNotManagedException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvitationRecoveryRequiredException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.OrganizationSuspendedException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrgAdminApiExceptionHandlerTest {

    private final OrgAdminApiExceptionHandler handler = new OrgAdminApiExceptionHandler();

    /**
     * Raised by {@code TenantRealmResolver} and owned by platform-admin, declared here because advice
     * scoping matches on the controller's package. Without this handler a bad {@code orgKey} on an
     * org-admin path would come back as {@code 500 internal-error}.
     */
    @Test
    void anUnknownTenantIs404() {
        var response = handler.handleOrganizationNotFound(new UnknownOrganizationException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.not-found");
    }

    /**
     * 404, not 403. From a suspended tenant's side the organization has stopped existing, and a 403
     * would invite a retry that cannot succeed until platform staff lift the suspension.
     */
    @Test
    void aSuspendedTenantIs404RatherThan403() {
        var response = handler.handleSuspended(new OrganizationSuspendedException("my-org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.suspended");
    }

    @Test
    void aNonAdministratorIs403() {
        var response = handler.handleAccessDenied(new OrganizationAccessDeniedException("my-org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.access-denied");
    }

    @Test
    void anUnknownUserIs404() {
        var response = handler.handleUserNotFound(new UserNotFoundException("my-org", "nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("user.not-found");
    }

    @Test
    void aDuplicateUserIs409() {
        var response = handler.handleUserExists(new UserAlreadyExistsException("my-org", "ada"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("user.already-exists");
    }

    @Test
    void anUndeclaredRoleIs400() {
        var response = handler.handleUnknownRole(new UnknownRoleException("my-org", "typo"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("role.unknown");
    }

    @Test
    void aMalformedOrReservedRoleNameIs400() {
        var response = handler.handleInvalidRoleName(new InvalidRoleNameException("Bad Name", "not a valid shape"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("role.invalid-name");
    }

    @Test
    void aRoleThatAlreadyExistsIs409() {
        var response = handler.handleRoleExists(new RoleAlreadyExistsException("my-org", "accounting"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("role.already-exists");
    }

    @Test
    void aRoleTheOrganizationDoesNotDeclareIs404() {
        var response = handler.handleRoleNotFound(new RoleNotFoundException("my-org", "ghost"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("role.not-found");
    }

    @Test
    void aRoleThePlatformOrTheDesignerOwnsIs403() {
        var response = handler.handleRoleNotManaged(new RoleNotManagedException("org-admin", "ProcessPuzzle"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("role.not-managed");
    }

    /** The body says what still uses the role, so the administrator knows what to release first. */
    @Test
    void aRoleStillInUseIs409WithWhatUsesIt() {
        var in = new RoleInUseException("accounting", true,
                List.of(new RoleReference("document", "reader-role", "doc-1", "Handbook")));

        var response = handler.handleRoleInUse(in);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("role.in-use");
        assertThat(response.getBody().getHeld()).isTrue();
        assertThat(response.getBody().getReferences()).singleElement().satisfies(reference -> {
            assertThat(reference.getFeature()).isEqualTo("document");
            assertThat(reference.getKind()).isEqualTo("reader-role");
            assertThat(reference.getId()).isEqualTo("doc-1");
            assertThat(reference.getTitle()).isEqualTo("Handbook");
        });
    }

    @Test
    void anUnreachableDirectoryIs503() {
        var response = handler.handleDirectoryUnavailable(
                new DirectoryUnavailableException("keycloak is down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("user-directory.unavailable");
    }

    @Test
    void anUnknownInvitationOutcomeIs409WithRecoveryIdentifiersRatherThanRetryable503() {
        var response = handler.handleInvitationRecovery(
                new InvitationRecoveryRequiredException("operation-1", "user-1",
                        new DirectoryUnavailableException("cleanup failed")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.getErrorId()).isEqualTo("user.invitation-recovery-required");
            assertThat(body.getErrorText()).contains("before retrying");
            assertThat(body.getOperationId()).isEqualTo("operation-1");
            assertThat(body.getUserId()).isEqualTo("user-1");
        });
    }

    @Test
    void anUnknownCreateDoesNotInventAUserId() {
        var response = handler.handleInvitationRecovery(
                new InvitationRecoveryRequiredException("operation-2", null,
                        new DirectoryUnavailableException("create response lost")));

        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.getOperationId()).isEqualTo("operation-2");
            assertThat(body.getUserId()).isNull();
        });
    }

    /**
     * Several advices on the FEATURE rung claim {@code OrganizationAccessDeniedException}, which is
     * {@code core}'s and therefore shared by all of them. Safe only while their scopes stay disjoint,
     * so losing {@code basePackages} here would make which one answers depend on bean ordering.
     *
     * <p>The unknown-tenant type is no longer among the shared ones: it is
     * {@link UnknownOrganizationException}, this module's own, rather than platform-admin's.
     */
    @Test
    void theAdviceIsScopedToThisModuleAndDeclaresItsRung() {
        RestControllerAdvice advice =
                OrgAdminApiExceptionHandler.class.getAnnotation(RestControllerAdvice.class);

        assertThat(advice).isNotNull();
        assertThat(advice.basePackages()).containsExactly("com.processpuzzle.orgadmin");
        assertThat(OrgAdminApiExceptionHandler.class.getAnnotation(Order.class))
                .isNotNull()
                .extracting(Order::value)
                .isEqualTo(ApiAdviceOrder.FEATURE);
    }
}
