/**
 * Base Artifact: interprets artifact descriptors to store, retrieve and describe the documents,
 * media and generated output an application produces and consumes.
 *
 * <p>Still a scaffold. {@code allowedDependencies} is declared empty rather than left off: the
 * feature genuinely depends on no other module today, and stating that means the first undeclared
 * edge someone adds fails the modularity test instead of passing unnoticed.
 */
@ApplicationModule(displayName = "Base Artifact", allowedDependencies = {})
package com.processpuzzle.artifact;

import org.springframework.modulith.ApplicationModule;
