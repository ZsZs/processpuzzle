/**
 * Ports base-entity asks the host application to implement, published as the {@code port} named interface so
 * an adapter in the application's composition root may implement them — the same arrangement base-state and
 * base-workflow use for theirs. Every port here has a {@code NONE} default, used when no adapter is supplied.
 */
@NamedInterface("port")
package com.processpuzzle.baseentity.port;

import org.springframework.modulith.NamedInterface;
