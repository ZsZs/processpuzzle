/**
 * Base AI: the platform's AI capabilities. The first is object recognition — telling which of a given
 * list of subjects is the one in front of the camera, from photos of them taken beforehand; see
 * {@code docs/ai/boat-recognition-design.md}. It knows nothing of what the answer is used for. The
 * models run in the separate vision server (apps/vision-server), shared infrastructure this module
 * reaches through its {@code VisionServer} port and its own HTTP adapter.
 *
 * <p>Depends on no other feature module. What it needs from the rest of the platform — object
 * storage, and whether a subject exists, what its identifier is and which photos it has — it
 * declares as the {@code MediaStore} and {@code SubjectDirectory} ports, which the composition root
 * implements over processpuzzle-store and base-entity.
 *
 * <p>Exposes {@code ai :: port} and {@code ai :: galleries}, for the composition root: the ports to
 * implement, and {@code SubjectGalleries} to tell this module that a subject changed. Everything else
 * stays internal.
 */
@ApplicationModule(
        displayName = "Base AI",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.ai;

import org.springframework.modulith.ApplicationModule;
