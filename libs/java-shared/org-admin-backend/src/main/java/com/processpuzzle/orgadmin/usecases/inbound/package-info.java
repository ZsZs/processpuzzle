/**
 * One class per operation on a tenant's user directory, plus
 * {@link com.processpuzzle.orgadmin.usecases.inbound.TenantRealmResolver} — the seam every one of
 * them goes through first, which is where the membership check, the unknown-tenant 404 and the
 * suspended-tenant refusal all live.
 *
 * <p>Not a named interface: nothing outside this module calls these. org-admin is a leaf.
 */
package com.processpuzzle.orgadmin.usecases.inbound;
