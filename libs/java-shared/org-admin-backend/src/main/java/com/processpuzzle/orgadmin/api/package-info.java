/**
 * The surface org-admin exposes to other Spring Modulith application modules: questions about a tenant's users
 * that need no administrative right to ask. Everything else — the directory port, invitations, role
 * assignment — stays internal, guarded by {@code TenantRealmResolver}.
 */
@NamedInterface("api")
package com.processpuzzle.orgadmin.api;

import org.springframework.modulith.NamedInterface;
