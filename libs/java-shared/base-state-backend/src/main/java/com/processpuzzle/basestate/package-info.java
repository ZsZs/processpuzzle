/**
 * Base State: interprets state and transition definitions to answer which transitions an entity may
 * take and what its current state projection is.
 *
 * <p>Still a scaffold. {@code allowedDependencies} is declared empty rather than left off: the
 * feature genuinely depends on no other module today, and stating that means the first undeclared
 * edge someone adds fails the modularity test instead of passing unnoticed.
 */
@ApplicationModule(displayName = "Base State", allowedDependencies = {})
package com.processpuzzle.basestate;

import org.springframework.modulith.ApplicationModule;
