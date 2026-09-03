package com.processpuzzle.platformadmin.usecase.port;

import java.util.List;
import java.util.UUID;

/**
 * Stand-in used when the deploying application configures no identity provider — mirroring how
 * {@link PermitAllOrganizationAccessPolicy} stands in for a missing access policy.
 *
 * <p><b>It creates no realms.</b> A deployment running on this has organizations that reach
 * {@code ACTIVE} with nothing behind them to log into, which is fine for a unit test or a local run
 * of a feature that does not touch authentication, and wrong for anything else. Its purpose is that
 * this library, and every test of it, is runnable without a Keycloak — the same reason the permit-all
 * policy exists.
 *
 * <p>{@link #createAdminUser} returns a fresh random id rather than a fixed one. A constant would
 * make two "created" users indistinguishable, and a test that then asserted on the id would pass for
 * the wrong reason.
 *
 * <p>Deliberately not a {@code @Component}: it is instantiated as a fallback by
 * {@code OrganizationRealmProvisioner}, so a real adapter never has to compete with it for
 * injection.
 */
public class NoOpIdentityRealmPort implements IdentityRealmPort {

    @Override
    public void createRealm(String realm, String displayName, String defaultLocale) {
        // no identity provider configured
    }

    @Override
    public void enableRealm(String realm) {
        // no identity provider configured
    }

    @Override
    public void disableRealm(String realm) {
        // no identity provider configured
    }

    @Override
    public void deleteRealm(String realm) {
        // no identity provider configured
    }

    @Override
    public String createAdminUser(String realm, NewUser user, List<String> roles) {
        return UUID.randomUUID().toString();
    }
}
