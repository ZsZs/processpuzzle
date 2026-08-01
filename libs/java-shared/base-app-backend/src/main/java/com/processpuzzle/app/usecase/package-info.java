/**
 * The use cases of the Base App feature, and the module's outward-facing surface.
 *
 * <p>Exposed as the {@code usecase} named interface. The nested {@code service} and {@code exception}
 * packages are not propagated and stay internal; {@code port} declares its own named interface
 * because implementing it is a different act from calling a use case.
 */
@NamedInterface("usecase")
package com.processpuzzle.app.usecase;

import org.springframework.modulith.NamedInterface;
