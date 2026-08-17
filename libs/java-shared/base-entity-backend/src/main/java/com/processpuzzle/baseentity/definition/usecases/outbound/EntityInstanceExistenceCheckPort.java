package com.processpuzzle.baseentity.definition.usecases.outbound;

/**
 * The definition module cannot query the instances module's repository directly across the
 * module boundary — this port is what DeleteEntityDefinitionUseCase depends on instead. The
 * implementing adapter (definition/adapters/outbound) is what actually reaches into the
 * instances module.
 */
public interface EntityInstanceExistenceCheckPort {

    boolean existsAnyInstanceOf(String entityDefinitionCode);
}
