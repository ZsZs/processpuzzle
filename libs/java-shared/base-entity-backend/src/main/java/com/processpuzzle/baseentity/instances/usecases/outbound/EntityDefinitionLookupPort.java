package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.Optional;

/**
 * What the instances module needs to know from the definition module — payload validation and
 * RSQL attribute-path resolution both go through this rather than a direct repository
 * dependency across the module boundary.
 */
public interface EntityDefinitionLookupPort {

    Optional<EntityDefinitionView> findByCode(String code);
}
