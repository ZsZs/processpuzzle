package com.processpuzzle.core.tenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is one behaviour to pin here and it is the default's direction.
 *
 * <p>{@link KnownRealms#NONE} is the value an application gets when it wires no adapter, and it is
 * the only port default in the platform that <em>denies</em>. Every other one permits, on the
 * principle that a library which cannot answer a question must not answer it with "no" — but a
 * permissive default here would have the resource server accept a token signed by a key from any
 * realm whose name it had never heard. The assertion below is therefore not a triviality: it is the
 * inversion, written down where a future edit would have to delete it on purpose.
 *
 * <p>The interface is exercised for real by {@code TenantAuthenticationManagerResolverTest} in the
 * testbed backend, which is the module that consumes it. That is also why this test exists at all —
 * the lambda lives here, so this module's coverage has to come from here.
 */
class KnownRealmsTest {

    @Test
    void theDefaultVouchesForNoRealmAtAll() {
        assertThat(KnownRealms.NONE.isKnown("processpuzzle-testbed")).isFalse();
        assertThat(KnownRealms.NONE.isKnown("any-tenant")).isFalse();
        assertThat(KnownRealms.NONE.isKnown("")).isFalse();
    }

    /**
     * A stack realm is trusted by configuration, not by this port — so {@code NONE} saying "no" to
     * the deployment's own realm is correct rather than a bug. The resolver checks the stack realm
     * before it ever asks.
     */
    @Test
    void theDefaultDoesNotSpeakForTheDeploymentsOwnRealm() {
        assertThat(KnownRealms.NONE.isKnown("processpuzzle-admin")).isFalse();
    }
}
