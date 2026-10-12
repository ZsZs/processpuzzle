package com.processpuzzle.baseentity.instances.usecases.outbound;

import com.processpuzzle.core.definition.Channel;
import java.util.Optional;

/**
 * What the instances module needs to know from the definition module — payload validation and
 * RSQL attribute-path resolution both go through this rather than a direct repository
 * dependency across the module boundary.
 */
public interface EntityDefinitionLookupPort {

    /** The definition the running application uses: the organization's released one. */
    default Optional<EntityDefinitionView> findByCode(String orgKey, String code) {
        return findByCode(orgKey, code, Channel.RELEASED);
    }

    /** Either row of a definition; the draft is for checking other drafts against, never for live data. */
    Optional<EntityDefinitionView> findByCode(String orgKey, String code, Channel channel);
}
