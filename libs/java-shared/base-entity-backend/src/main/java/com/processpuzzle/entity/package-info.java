/**
 * Base Entity: interprets entity descriptors and their attribute descriptors to serve the generated
 * form, table, search and export surfaces of {@code @processpuzzle/base-entity}.
 *
 * <p>Still a scaffold — entities are served today by {@code processpuzzle-store}, plain REST or
 * Firestore. {@code allowedDependencies} is declared empty rather than left off: the feature
 * genuinely depends on no other module today, and stating that means the first undeclared edge
 * someone adds fails the modularity test instead of passing unnoticed.
 */
@ApplicationModule(displayName = "Base Entity", allowedDependencies = {})
package com.processpuzzle.entity;

import org.springframework.modulith.ApplicationModule;
