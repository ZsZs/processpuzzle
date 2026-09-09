/**
 * The deploying application's identity layer: the resource server, and the real implementations of
 * the two ports the feature libraries declare for it.
 *
 * <p>This lives in the application rather than in a library on purpose. The libraries have no Spring
 * Security on their classpath and no opinion about who the caller is — they declare
 * {@code OrganizationAccessPolicy} and {@code RoleMembershipPort} and fall back to permitting
 * everything when nothing implements them. This package is what stops that fallback being what runs.
 *
 * <p>Read {@link com.processpuzzle.security.SecurityConfig} before deploying: the tenant API is open
 * without a token by default, and the reason is spelled out there.
 */
package com.processpuzzle.security;
