/**
 * The tenant's user directory, which this module implements, and which other features may consult.
 *
 * <p>A named interface, {@code org-admin :: port}, so that a host application can put the directory in front of
 * another feature's own port — base-calendar's invitations, for one — in its composition root. The adapters stay in
 * {@code adapters.outbound} of this module, and a consumer gets the port's types, not the Keycloak client behind them.
 */
@org.springframework.modulith.NamedInterface("port")
package com.processpuzzle.orgadmin.usecases.outbound;
