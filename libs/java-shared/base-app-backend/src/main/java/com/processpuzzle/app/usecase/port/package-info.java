/**
 * The one port a host application implements to feed Base App information it cannot own itself:
 * which entity names exist ({@link com.processpuzzle.app.usecase.port.EntityNameRegistry}).
 *
 * <p>{@code OrganizationAccessPolicy} used to be the second, and moved to
 * {@code com.processpuzzle.platformadmin.usecase.port} with the {@code Organization} aggregate — who
 * may act on a tenant is a platform question, not an app-shell one. Base App still consumes it, now
 * through {@code platformadmin :: port}, so an application that already supplied a policy bean needs
 * only to change the interface it implements.
 *
 * <p>Exposed as the {@code port} named interface, separately from {@code usecase}: this is the side
 * of the module other code plugs into, not the side it calls.
 */
@NamedInterface("port")
package com.processpuzzle.app.usecase.port;

import org.springframework.modulith.NamedInterface;
