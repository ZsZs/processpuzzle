package com.processpuzzle.core.tenancy;

/**
 * Which identity realms, beyond this deployment's own, issue tokens the resource server will
 * validate.
 *
 * <p>Realm-per-tenant means the set of trusted issuers is not configuration: it grows when a tenant
 * is provisioned. The resource server therefore cannot hold a static issuer list, and something has
 * to answer "is this realm one of ours" for an issuer it has not seen before. That question used to
 * be answered by injecting {@code platform-admin}'s {@code OrganizationRepository} straight into the
 * application's security layer — a tenant-registry read on the authentication path, in the one module
 * that is supposed to be extractable.
 *
 * <p><b>The deployment's own stack realm is not this port's business.</b> It is configuration
 * ({@code processpuzzle.security.stack-realm}), it is trusted unconditionally, and it is trusted
 * whether or not any bean implements this interface. This port answers only the question
 * configuration cannot: which <em>additional</em>, tenant-owned realms exist.
 *
 * <h2>Why this interface is in core</h2>
 *
 * <p>It has no implementation here and no consumer here — both live in applications. It sits in core
 * because <em>two</em> applications need the same interface: the public testbed backend, which has no
 * tenant registry and wants the default, and the private admin backend, which implements it over the
 * organization registry. An interface duplicated across two repositories is an interface that drifts,
 * and the shared library is the only place both can see.
 *
 * <h2>The default denies, and that is not a degradation</h2>
 *
 * <p>{@link #NONE} answers "no additional realms", so an application that wires nothing trusts
 * exactly one realm: its own. Note that this runs opposite to every other port in the platform,
 * where a library that cannot answer a question must not answer it with "no" — a permissive default
 * here would mean accepting a token signed by a key from an arbitrary realm, which is not a
 * degraded answer but a broken one.
 *
 * <p>For a single-realm deployment the default is also the <em>complete</em> answer rather than a
 * fallback, which is why the testbed backend wires no adapter and loses nothing by it.
 */
public interface KnownRealms {

    /** Trusts no realm but the deployment's own. Correct, not merely safe, for a single-realm stack. */
    KnownRealms NONE = realm -> false;

    /**
     * Whether {@code realm} is a tenant realm of this platform.
     *
     * <p>Called at most once per realm per process — the caller caches successes — but it is on the
     * authentication path, so an implementation reaching a database or a network should expect to be
     * reached by any request carrying an unrecognised issuer, and the caller is responsible for
     * gating that (see the prefix check in the testbed backend's resolver).
     *
     * @param realm a single path segment, never blank and never containing a slash
     */
    boolean isKnown(String realm);
}
