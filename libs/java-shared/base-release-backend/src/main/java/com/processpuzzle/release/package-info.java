/**
 * Base Release: releasing an organization's definitions in one step. A designer edits drafts of every
 * definition; the running application reads only what was released. A release asks every feature
 * what it would change, what its drafts refer to and what would object, refuses on any error, and
 * otherwise releases every kind in one transaction and records what it carried.
 * See {@code docs/definition-release.md}.
 *
 * <p>Depends on no feature module. Each feature contributes a
 * {@link com.processpuzzle.core.definition.ReleaseParticipant} for its own definition kind; this
 * module collects them as beans and runs them in order.
 */
@ApplicationModule(
        displayName = "Base Release",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.release;

import org.springframework.modulith.ApplicationModule;
