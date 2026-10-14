package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.security.SecurityProperties;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationDisabledException;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort;
import org.springframework.stereotype.Service;

/**
 * Self-registration into the realm this deployment serves.
 *
 * <p>Refused outright, as 404, in two cases: the deployment has not enabled it, and the deployment
 * runs Organizations in a shared realm — there, accounts only come from an organization's
 * administrator, so an open sign-up would mean an account with no organization.
 *
 * <p>The rate limiter is consulted only once the feature is known to be enabled, so a disabled
 * deployment holds no per-address state.
 */
@Service
public class RegisterUser {

    private final RegistrationProperties properties;
    private final SecurityProperties security;
    private final KeycloakAdminClient client;
    private final RegistrationRateLimiter limiter;
    private final SelfRegistrationPort registrations;

    public RegisterUser(RegistrationProperties properties, SecurityProperties security,
                        KeycloakAdminClient client, RegistrationRateLimiter limiter,
                        SelfRegistrationPort registrations) {
        this.properties = properties;
        this.security = security;
        this.client = client;
        this.limiter = limiter;
        this.registrations = registrations;
    }

    public void execute(String clientKey, SelfRegistrationPort.Registration registration) {
        if (!properties.isEnabled() || client.organizationRealm().isPresent()) {
            throw new RegistrationDisabledException("Self-registration is not available.");
        }
        limiter.acquire(clientKey);
        registrations.register(security.getStackRealm(), registration, properties.isVerifyEmail());
    }
}
