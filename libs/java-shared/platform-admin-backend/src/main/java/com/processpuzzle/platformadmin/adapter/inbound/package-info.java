/**
 * Inbound adapters: the {@code /platform/**} REST controller and its mapper, the advice that turns
 * this feature's refusals into {@code ErrorResponse} bodies, the startup seeder for the plan catalog,
 * and {@link com.processpuzzle.platformadmin.adapter.inbound.OrganizationRealmProvisioner} — which
 * is "inbound" in the hexagonal sense despite reaching outward: it is driven by a domain event
 * rather than by a caller.
 *
 * <p>Not a named interface. Nothing outside this module calls a controller.
 */
package com.processpuzzle.platformadmin.adapter.inbound;
