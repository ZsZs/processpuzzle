/**
 * The resource-server mechanism: how a Keycloak bearer token is validated on this platform, whose
 * realm issued it, what it may do, and what a refusal looks like on the wire.
 *
 * <p><b>Mechanism only. No policy.</b> Nothing here decides which paths are closed — that is a
 * {@code SecurityFilterChain}, it differs per application, and it stays in the application. What
 * lives here is the part that was identical in every deployment and therefore had no business being
 * copied into each one: realm-per-tenant issuer resolution
 * ({@link com.processpuzzle.core.security.TenantAuthenticationManagerResolver}), the
 * {@code realm_access.roles} claim mapping
 * ({@link com.processpuzzle.core.security.RealmRoleConverter}), reading the principal out of the
 * security context ({@link com.processpuzzle.core.security.CurrentPrincipal}), the JSON body on a
 * 401/403/503 ({@link com.processpuzzle.core.security.ApiSecurityErrorHandler}) and the
 * {@code processpuzzle.security.*} properties that configure all of it.
 *
 * <h2>This package used to argue it belonged in an application</h2>
 *
 * <p>It lived in {@code apps/processpuzzle-testbed-backend} as {@code com.processpuzzle.security},
 * and its package-info said so deliberately: "the libraries have no Spring Security on their
 * classpath and no opinion about who the caller is". That reason was sound and is <em>unchanged</em>
 * — it is simply not a reason to live in an application, because the {@code <optional>true</optional>}
 * dependency in this module's pom preserves it exactly. A consumer of any ProcessPuzzle library
 * still gets no Spring Security unless it declares the starter itself, and without the starter these
 * classes are absent from its classpath rather than inert on it. Read that pom comment before making
 * either starter non-optional: {@code processpuzzle-biz-backend} reaches this artifact transitively
 * and has no security of its own, so the change would put a public sign-up funnel behind
 * generated-password HTTP Basic.
 *
 * <p>What did change is the number of applications. With one deployment, "copy" and "share" are the
 * same cost; with a second ({@code processpuzzle-admin-backend}) the copy became the more expensive
 * one, and the drift is not hypothetical — the CORS configuration that was duplicated the same way
 * was simply never written for the admin stack, so that backend rejected every browser call while
 * its environment file looked correct.
 *
 * <h2>What stayed behind, and why</h2>
 *
 * <ul>
 *   <li><b>{@code SecurityConfig}</b> — policy. Testbed leaves its tenant API open unless
 *       {@code require-authentication} is set, because its frontends send no token; Admin is closed
 *       unconditionally, because its frontend does. One chain cannot express both honestly.
 *   <li><b>{@code JwtOrganizationAccessPolicy}</b> — the {@code OrganizationAccessPolicy}
 *       implementation. It reads only what {@link com.processpuzzle.core.security.CurrentPrincipal}
 *       exposes, so it <em>could</em> move, but its documented behaviour for an unauthenticated
 *       caller is testbed's compromise rather than a platform rule.
 *   <li><b>{@code RealmRoleMembershipPolicy}</b> — cannot move. It implements base-workflow's
 *       {@code RoleMembershipPort}, and {@code base-workflow-backend} already depends on this
 *       artifact; moving it would make the dependency circular.
 * </ul>
 *
 * <p>No {@code package-info} module declaration of its own: {@code com.processpuzzle.core} is an
 * {@link org.springframework.modulith.ApplicationModule.Type#OPEN} module, so its sub-packages are
 * part of its API and exempt from dependency verification.
 */
package com.processpuzzle.core.security;
