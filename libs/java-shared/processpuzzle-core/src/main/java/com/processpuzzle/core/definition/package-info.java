/**
 * The definition-import SPI: how a Business Starter bundle reaches the feature libraries without the
 * importer depending on any of them.
 *
 * <p>Each feature implements {@link com.processpuzzle.core.definition.DefinitionImportParticipant} for
 * its own definition kind; {@code base-starter-backend} collects the participants as beans and runs
 * them in {@link com.processpuzzle.core.definition.DefinitionImportParticipant#order()} inside one
 * transaction. See {@code docs/business-starters/business-starters-design.md}.
 */
package com.processpuzzle.core.definition;
