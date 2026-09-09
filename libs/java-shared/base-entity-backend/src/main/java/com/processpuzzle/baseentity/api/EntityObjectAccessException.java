package com.processpuzzle.baseentity.api;

import java.util.UUID;

/**
 * The failure modes of {@link EntityObjectAccess}, declared here rather than reused from
 * {@code baseentity.common}.
 *
 * <p>A published interface's exceptions are part of its contract, and a caller must be able to
 * handle them without importing anything outside the named interface — {@code baseentity.common} is
 * module-internal, so a caller catching {@code NotFoundException} would be reaching past the
 * boundary this package exists to draw. These are plain {@link RuntimeException}s and deliberately
 * not subclasses of the internal ones: nothing routes them to base-entity's REST error handler,
 * because no controller calls this interface. The in-process caller translates them into its own
 * vocabulary — see base-state's {@code BaseEntityObjectGateway}.
 */
public sealed class EntityObjectAccessException extends RuntimeException {

    private EntityObjectAccessException(String message) {
        super(message);
    }

    /** No object with this id, or one that exists but is not of the named entity type. */
    public static final class NotFound extends EntityObjectAccessException {
        public NotFound(String entityDefinitionCode, UUID objectId) {
            super("No '%s' instance with id '%s'".formatted(entityDefinitionCode, objectId));
        }
    }

    /** {@code expectedVersion} no longer matches — someone else wrote between read and write. */
    public static final class VersionConflict extends EntityObjectAccessException {
        public VersionConflict(UUID objectId, long expectedVersion, long actualVersion) {
            super("Entity instance '%s' is at version %d, not the expected %d"
                .formatted(objectId, actualVersion, expectedVersion));
        }
    }
}
