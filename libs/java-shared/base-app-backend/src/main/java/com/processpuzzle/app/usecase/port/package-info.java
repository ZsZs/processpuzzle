/**
 * Ports a host application implements to feed Base App information it cannot own itself: which
 * entity names exist ({@link com.processpuzzle.app.usecase.port.EntityNameRegistry}) and who may act
 * on an organization ({@link com.processpuzzle.app.usecase.port.OrganizationAccessPolicy}).
 *
 * <p>Exposed as the {@code port} named interface, separately from {@code usecase}: this is the side
 * of the module other code plugs into, not the side it calls.
 */
@NamedInterface("port")
package com.processpuzzle.app.usecase.port;

import org.springframework.modulith.NamedInterface;
