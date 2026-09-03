/**
 * The one port a host application implements to feed Base App information it cannot own itself:
 * which entity names exist ({@link com.processpuzzle.app.usecase.port.EntityNameRegistry}).
 *
 * <p>{@code OrganizationAccessPolicy} used to be the second. It is now
 * {@link com.processpuzzle.core.tenancy.OrganizationAccessPolicy}: it went to platform-admin with the
 * {@code Organization} aggregate, then on to core, because deciding who the caller is turned out to
 * belong to no feature at all. Base App still consumes it — through {@code core}, which costs it
 * nothing, where consuming it through {@code platformadmin :: port} had cost it a compile dependency
 * on that whole module.
 *
 * <p>Exposed as the {@code port} named interface, separately from {@code usecase}: this is the side
 * of the module other code plugs into, not the side it calls.
 */
@NamedInterface("port")
package com.processpuzzle.app.usecase.port;

import org.springframework.modulith.NamedInterface;
