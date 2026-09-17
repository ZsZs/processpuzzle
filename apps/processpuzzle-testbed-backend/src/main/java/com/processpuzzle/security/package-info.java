/**
 * This deployment's security <em>policy</em>: which paths are closed, and the real implementations of
 * the two ports the feature libraries declare.
 *
 * <p>The mechanism no longer lives here. Token validation, issuer resolution, claim mapping, the
 * principal accessor, the 401/403/503 body and the {@code processpuzzle.security.*} properties moved
 * to {@link com.processpuzzle.core.security} once a second application
 * ({@code processpuzzle-admin-backend}) needed them; that package's {@code package-info} records why,
 * and why the starters it depends on stay {@code optional}. What stayed is what genuinely differs
 * between deployments, plus one thing that cannot move:
 *
 * <ul>
 *   <li>{@link com.processpuzzle.security.SecurityConfig} — the filter chain. Policy: this
 *       deployment's tenant API is open without a token unless
 *       {@code processpuzzle.security.require-authentication} is set, because its Angular
 *       applications send none.
 *   <li>{@link com.processpuzzle.security.JwtOrganizationAccessPolicy} — the
 *       {@code OrganizationAccessPolicy} that replaces {@code PermitAllOrganizationAccessPolicy}.
 *       Its treatment of an unauthenticated caller is this deployment's compromise, not a platform
 *       rule, which is why it did not move with the mechanism.
 *   <li>{@link com.processpuzzle.security.RealmRoleMembershipPolicy} — the
 *       {@code RoleMembershipPort}. It <em>cannot</em> move: it implements a base-workflow port, and
 *       {@code base-workflow-backend} already depends on {@code processpuzzle-core}, so promoting it
 *       would close a dependency cycle.
 * </ul>
 *
 * <p>The libraries still have no Spring Security on their classpath and no opinion about who the
 * caller is — they declare {@code OrganizationAccessPolicy} and {@code RoleMembershipPort} and fall
 * back to permitting everything when nothing implements them. This package is what stops that
 * fallback being what runs.
 *
 * <p>Read {@link com.processpuzzle.security.SecurityConfig} before deploying: the tenant API is open
 * without a token by default, and the reason is spelled out there.
 */
package com.processpuzzle.security;
