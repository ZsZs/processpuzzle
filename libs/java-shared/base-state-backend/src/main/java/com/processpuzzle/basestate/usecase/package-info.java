/**
 * The use cases of the Base State feature, and the module's outward-facing surface: the
 * knowledge-layer state machine definition CRUD/import/export, and the operation-layer {@code
 * GetEntityObjectState} / {@code FireStateTransition} pair that is the single legitimate way an
 * {@code EntityObject}'s state attribute ever changes.
 *
 * <p>Exposed as the {@code usecase} named interface. The nested {@code service} and {@code
 * exception} packages are not propagated and stay internal; {@code port} declares its own named
 * interface because implementing it is a different act from calling a use case.
 */
@NamedInterface("usecase")
package com.processpuzzle.basestate.usecase;

import org.springframework.modulith.NamedInterface;
