package com.processpuzzle.baseentity.api;

import java.util.UUID;

/**
 * Read and single-attribute write access to one {@code EntityObject}, for a module that owns an
 * attribute of it without owning the object.
 *
 * <p>base-state is the caller, through its {@code EntityObjectGateway} adapter: it is the only
 * legitimate writer of the attribute a state machine's {@code stateAttributeKey} names, and this is
 * the channel it writes through.
 */
public interface EntityObjectAccess {

    /**
     * @throws EntityObjectAccessException.NotFound if no object with {@code objectId} exists, or if
     *         it exists but is not of type {@code entityDefinitionCode} — an id from the wrong type
     *         is a caller error, not an empty result
     */
    EntityObjectView find(String entityDefinitionCode, UUID objectId);

    /**
     * Writes {@code value} at {@code attributeCode} and leaves every other key of the payload
     * exactly as it was — the "PUT one field, clobber nothing" discipline, since the caller owns one
     * attribute and knows nothing about the rest.
     *
     * <p>A compare-and-swap against {@code expectedVersion}: this is the tail of a read-then-act
     * sequence on the caller's side (read the state, resolve the transition, write the new state),
     * so a version that has moved in between means someone else acted and the write must not land.
     *
     * <p>Payload validation is deliberately <em>not</em> re-run. The caller is changing one
     * attribute to a value its own metadata already constrains, and re-validating would make an
     * unrelated pre-existing violation elsewhere in the payload block a legitimate state change.
     *
     * @return the object's version after this write
     * @throws EntityObjectAccessException.NotFound        if no such object exists
     * @throws EntityObjectAccessException.VersionConflict  if {@code expectedVersion} does not match
     *         the object's current version
     */
    long updateAttribute(String entityDefinitionCode, UUID objectId, String attributeCode,
                         String value, long expectedVersion);
}
