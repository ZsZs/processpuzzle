/**
 * Outbound adapter: the tenant's user directory over Keycloak's Admin REST API, and the
 * configuration that chooses between it and the no-op.
 *
 * <p>Not a named interface — unlike platform-admin's outbound package, nothing outside this module
 * needs to reach in here. org-admin is a leaf.
 */
package com.processpuzzle.orgadmin.adapters.outbound;
