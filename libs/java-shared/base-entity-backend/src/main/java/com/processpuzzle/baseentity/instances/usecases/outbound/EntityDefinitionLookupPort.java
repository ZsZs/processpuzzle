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

    /**
     * The common that shares the entity type {@code code} with {@code orgKey}: the organization whose released
     * definition of that code is a shared type, when {@code orgKey} is a member of it and defines no type of that
     * code itself. Its objects are stored under that key, and {@link #findByCode} answers its definition.
     */
    /** Whether the type {@code code} is a shared type as {@code orgKey} sees it: its own, when it is a common, or its common's. */
    default boolean isShared(String orgKey, String code) {
        return sharedFrom(orgKey, code).isPresent();
    }

    default Optional<String> sharedFrom(String orgKey, String code) {
        return Optional.empty();
    }
}
