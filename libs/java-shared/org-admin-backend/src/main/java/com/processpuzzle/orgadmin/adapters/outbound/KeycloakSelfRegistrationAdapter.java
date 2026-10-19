package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.PasswordPolicyViolationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a self-registered account through Keycloak's Admin API, in one call: the user is created
 * with its credential, so there is no window in which an enabled account exists without a password.
 *
 * <p>The password travels only in the request body to Keycloak and is never logged. The email is the
 * username, which is what the realm's {@code loginWithEmailAllowed} login expects.
 *
 * <p>Requesting the verification mail is best effort: the account already carries the
 * {@code VERIFY_EMAIL} required action, so a failed send leaves a registration that can still be
 * completed from Keycloak's own "resend" prompt at first login.
 */
@Component
public class KeycloakSelfRegistrationAdapter implements SelfRegistrationPort {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSelfRegistrationAdapter.class);

    private final KeycloakAdminClient client;

    public KeycloakSelfRegistrationAdapter(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public void register(String realm, Registration registration, boolean verifyEmail) {
        if (!client.isConfigured()) {
            throw new DirectoryUnavailableException("No identity provider is configured for registration.");
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", registration.email());
        user.put("email", registration.email());
        user.put("firstName", registration.firstName());
        user.put("lastName", registration.lastName());
        user.put("enabled", true);
        user.put("emailVerified", false);
        user.put("credentials", List.of(Map.of(
                "type", "password", "value", registration.password(), "temporary", false)));
        if (verifyEmail) {
            user.put("requiredActions", List.of("VERIFY_EMAIL"));
        }

        String userId;
        try {
            userId = client.createAndReturnId(new RealmScope(realm).adminRealmPath() + "/users", user).orElse(null);
        } catch (IdentityProviderUnavailableException ex) {
            throw translate(ex);
        }
        if (verifyEmail && userId != null) {
            requestVerificationMail(realm, userId);
        }
    }

    private void requestVerificationMail(String realm, String userId) {
        try {
            client.exchange(HttpMethod.PUT,
                    new RealmScope(realm).adminRealmPath() + "/users/" + userId + "/execute-actions-email",
                    List.of("VERIFY_EMAIL"), null);
        } catch (IdentityProviderUnavailableException ex) {
            LOG.warn("Registered user {} in realm {} but could not send the verification mail.", userId, realm, ex);
        }
    }

    private static RuntimeException translate(IdentityProviderUnavailableException ex) {
        int status = ex.getHttpStatus().orElse(0);
        if (status == KeycloakAdminClient.HTTP_CONFLICT) {
            return new UserAlreadyExistsException("An account with this email already exists.");
        }
        if (status == 400) {
            return new PasswordPolicyViolationException("The password or profile was refused by the identity provider.");
        }
        return new DirectoryUnavailableException("Registration is temporarily unavailable.", ex);
    }
}
