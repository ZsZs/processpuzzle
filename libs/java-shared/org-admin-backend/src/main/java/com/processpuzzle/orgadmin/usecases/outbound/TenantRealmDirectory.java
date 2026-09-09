package com.processpuzzle.orgadmin.usecases.outbound;

import java.util.Optional;

/**
 * Which identity realm administers {@code orgKey}, and whether that tenant may be administered at
 * all.
 *
 * <p>Every operation in this module begins by answering that, and the answer used to come from
 * injecting {@code platform-admin}'s {@code FindOrganization} — the only place one feature library
 * compiled against another. Stated as a port, the question stays and the edge goes: a deployment
 * that owns a tenant registry answers from it, and one that does not gets
 * {@link #BY_CONVENTION}.
 *
 * <h2>Two fields, and neither is an {@code OrganizationStatus}</h2>
 *
 * <p>The projection is deliberately narrower than the aggregate it will usually be built from. It
 * carries a realm name and one bit, because one bit is the whole of what this module does with a
 * tenant's lifecycle: {@code SUSPENDED} and {@code PROVISIONING} are refused identically, and
 * every other state is let through. Duplicating a four-constant lifecycle enum over here would
 * invite org-admin to grow opinions about states it does not own and cannot transition — the
 * translation belongs in the adapter, where the vocabulary is still the registry's.
 *
 * <p><b>The realm is returned rather than assumed.</b> A registry may canonicalise a key that
 * arrived in the wrong case, and a deployment may one day name realms differently from org keys;
 * either way the caller must use the value that comes back and not the string it passed in.
 *
 * <h2>The default permits, unlike {@code KnownRealms}</h2>
 *
 * <p>{@link #BY_CONVENTION} answers every key, mapping it to a realm of the same name and calling it
 * administerable. That is the platform's own naming rule — realm name, organization key and bucket
 * prefix are one string — so it is the right answer wherever no registry exists, and it keeps the
 * usual direction: a library that cannot answer a question must not answer it with "no". Refusing
 * here would take org-admin out of service in exactly the deployments that have no tenant lifecycle
 * to enforce.
 *
 * <p>Contrast {@code com.processpuzzle.core.tenancy.KnownRealms}, whose default denies: that one
 * decides whose signing keys to trust, so a permissive default would be a security hole rather than
 * a degraded answer. The two ports look alike and their defaults run opposite ways on purpose.
 *
 * <p>Not a named interface: nothing inside this build implements it. The adapter that will belongs
 * to the application that composes org-admin next to a tenant registry — for the commercial
 * product, the private repository's composition root over {@code FindOrganization}.
 */
public interface TenantRealmDirectory {

    /**
     * Realm name equals organization key, every tenant administerable. Correct wherever no tenant
     * registry is deployed, which includes the whole of the public repository.
     */
    TenantRealmDirectory BY_CONVENTION = orgKey -> Optional.of(new Tenant(orgKey, true));

    /**
     * @param orgKey the key from the request path, not yet trusted
     * @return empty when no such tenant exists, which the caller turns into a 404
     */
    Optional<Tenant> find(String orgKey);

    /**
     * What org-admin needs to know about a tenant, and nothing more — no name, no locale, no
     * subscription. Keeping the projection this narrow is what stops the port from drifting back
     * into a copy of somebody else's aggregate.
     *
     * @param realm the realm to administer; the value the caller must use
     * @param administerable whether the realm exists and is enabled — false while a tenant is being
     *        provisioned or after it has been suspended
     */
    record Tenant(String realm, boolean administerable) {
    }
}
