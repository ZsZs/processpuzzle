package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.TenantScope;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvitationRecoveryRequiredException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort.NewUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Compensates only the account identified by this attempt's successful create response. */
final class KeycloakInvitation {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakInvitation.class);

    private final KeycloakAdminClient client;

    KeycloakInvitation(KeycloakAdminClient client) {
        this.client = client;
    }

    DirectoryUser invite(TenantScope scope, String directory, NewUser user, List<String> roles,
                         Consumer<String> provision, Function<String, DirectoryUser> read) {
        String operationId = UUID.randomUUID().toString();
        String usersPath = scope.adminRealmPath() + "/users";
        LOG.info("Starting invitation '{}' in realm '{}' for directory '{}', username '{}'.",
                operationId, scope.realm(), directory, user.username());
        String userId = create(usersPath, directory, user, operationId);
        LOG.info("Invitation '{}' created account '{}'.", operationId, userId);
        try {
            provision.accept(userId);
            verifyRoles(read.apply(userId), roles);
            client.exchange(HttpMethod.PUT, usersPath + "/" + userId, Map.of("enabled", true), null);
            DirectoryUser result = read.apply(userId);
            verifyRoles(result, roles);
            if (!result.enabled()) {
                throw new DirectoryUnavailableException("The invited account could not be confirmed enabled.");
            }
            LOG.info("Invitation '{}' completed for account '{}'.", operationId, userId);
            return result;
        } catch (DirectoryUnavailableException | IdentityProviderUnavailableException failure) {
            compensate(usersPath, userId, operationId, failure);
            throw new DirectoryUnavailableException(
                    "Invitation failed; the newly created account was removed. It is safe to retry.", failure);
        }
    }

    private String create(String usersPath, String directory, NewUser user, String operationId) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("username", user.username());
        representation.put("email", user.email());
        representation.put("firstName", user.firstName());
        representation.put("lastName", user.lastName());
        representation.put("enabled", false);
        representation.put("emailVerified", false);
        representation.put("requiredActions", List.of("UPDATE_PASSWORD"));
        try {
            return client.createAndReturnId(usersPath, representation)
                    .filter(id -> !id.isBlank())
                    .orElseThrow(() -> recoveryRequired(operationId, null,
                            new DirectoryUnavailableException("Keycloak returned no created account id.")));
        } catch (IdentityProviderUnavailableException failure) {
            if (failure.getHttpStatus().stream().anyMatch(status -> status == KeycloakAdminClient.HTTP_CONFLICT)) {
                throw new UserAlreadyExistsException(directory, user.username());
            }
            // Even a server error can follow a committed create. Never search by username and
            // adopt or delete an account whose ownership this attempt cannot prove.
            throw recoveryRequired(operationId, null, failure);
        }
    }

    private void compensate(String usersPath, String userId, String operationId, RuntimeException failure) {
        try {
            client.exchangeTolerating(HttpMethod.DELETE, usersPath + "/" + userId, null,
                    KeycloakAdminClient.HTTP_NOT_FOUND);
        } catch (IdentityProviderUnavailableException cleanup) {
            failure.addSuppressed(cleanup);
            throw recoveryRequired(operationId, userId, failure);
        }
        LOG.warn("Invitation '{}' failed; account '{}' was removed.", operationId, userId, failure);
    }

    private static void verifyRoles(DirectoryUser user, List<String> roles) {
        if (roles != null && !user.roles().containsAll(roles)) {
            throw new DirectoryUnavailableException("The invited account does not hold every requested role.");
        }
    }

    private static InvitationRecoveryRequiredException recoveryRequired(
            String operationId, String userId, RuntimeException failure) {
        InvitationRecoveryRequiredException exception =
                new InvitationRecoveryRequiredException(operationId, userId, failure);
        LOG.error("Invitation '{}' requires recovery; confirmed account id: '{}'.", operationId, userId, exception);
        return exception;
    }
}
