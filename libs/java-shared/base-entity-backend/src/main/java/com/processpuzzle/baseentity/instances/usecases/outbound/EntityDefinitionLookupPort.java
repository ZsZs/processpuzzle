package com.processpuzzle.baseentity.instances.usecases.outbound;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
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

    /** The sharing policy of a shared type, as plain names so the instances module need not know the definition's enums. */
    record SharingView(String write, String visibility, List<String> visibleWhen, String from, String fromType) {
        public static final String OWNER_ORG = "OWNER_ORG";
        public static final String OWNER_ONLY = "OWNER_ONLY";

        public SharingView {
            visibleWhen = visibleWhen == null ? List.of() : List.copyOf(visibleWhen);
        }

        public SharingView(String write, String visibility, List<String> visibleWhen) {
            this(write, visibility, visibleWhen, null, null);
        }

        public SharingView(String write, String visibility) {
            this(write, visibility, List.of());
        }

        /** Whether the records follow another record, whose owner and visibility they take. */
        public boolean following() {
            return from != null && fromType != null;
        }

        /** Whether non-owners see a record only in some states of its state machine. */
        public boolean limitedByState() {
            return !visibleWhen.isEmpty();
        }

        public boolean ownerWrites() {
            return OWNER_ORG.equals(write);
        }

        public boolean ownerOnly() {
            return OWNER_ONLY.equals(visibility);
        }
    }

    /** The sharing policy of the type {@code code} as {@code orgKey} sees it; empty for a tenant type. */
    default Optional<SharingView> sharingOf(String orgKey, String code) {
        return Optional.empty();
    }

    /** Whether the type {@code code} is a shared type as {@code orgKey} sees it: its own, when it is a common, or its common's. */
    default boolean isShared(String orgKey, String code) {
        return sharedFrom(orgKey, code).isPresent();
    }

    /**
     * The common that shares the entity type {@code code} with {@code orgKey}: the organization whose released
     * definition of that code is a shared type, when {@code orgKey} is a member of it and defines no type of that
     * code itself. Its objects are stored under that key, and {@link #findByCode} answers its definition.
     */
    default Optional<String> sharedFrom(String orgKey, String code) {
        return Optional.empty();
    }
}
