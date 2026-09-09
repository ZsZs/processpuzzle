/**
 * Outbound ports the deploying application implements. Exposed as the {@code port} named interface
 * so an application can supply a {@link com.processpuzzle.document.usecase.port.DocumentAccessPolicy}
 * without being granted access to the rest of this module's internals.
 */
@NamedInterface("port")
package com.processpuzzle.document.usecase.port;

import org.springframework.modulith.NamedInterface;
