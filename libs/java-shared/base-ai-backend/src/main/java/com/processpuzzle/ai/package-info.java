/**
 * Base AI: the platform's AI capabilities. The first is object recognition — enrolling identifiable
 * subjects from photos and identifying them in uploaded videos; see
 * {@code docs/ai/boat-recognition-design.md}. The models run in the separate vision server
 * (apps/vision-server), shared infrastructure this module reaches through its {@code VisionServer}
 * port and its own HTTP adapter.
 *
 * <p>Depends on no other feature module. What it needs from the rest of the platform — object
 * storage, and whether a subject exists and what its identifier is — it declares as the
 * {@code MediaStore} and {@code SubjectDirectory} ports, which the composition root implements over
 * processpuzzle-store and base-entity.
 *
 * <p>Exposes {@code ai :: port}, for the composition root. Everything else stays internal.
 */
@ApplicationModule(
        displayName = "Base AI",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.ai;

import org.springframework.modulith.ApplicationModule;
