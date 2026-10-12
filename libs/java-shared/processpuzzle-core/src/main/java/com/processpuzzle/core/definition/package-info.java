/**
 * The definition SPIs: how a Business Starter bundle, and a release of an organization, reach the
 * feature libraries without the importer or the releaser depending on any of them.
 *
 * <p>Each feature implements {@link com.processpuzzle.core.definition.DefinitionImportParticipant} for
 * its own definition kind; {@code base-starter-backend} collects the participants as beans and runs
 * them in {@link com.processpuzzle.core.definition.DefinitionImportParticipant#order()} inside one
 * transaction. See {@code docs/business-starters/business-starters-design.md}.
 *
 * <p>Likewise each feature whose definitions a designer edits as drafts implements
 * {@link com.processpuzzle.core.definition.ReleaseParticipant}, and {@code base-release-backend} releases
 * all of them at once. A definition is stored twice, once per
 * {@link com.processpuzzle.core.definition.Channel}. See {@code docs/definition-release.md}.
 */
package com.processpuzzle.core.definition;
