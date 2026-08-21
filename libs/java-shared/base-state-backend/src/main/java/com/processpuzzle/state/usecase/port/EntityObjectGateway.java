package com.processpuzzle.state.usecase.port;

import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;

import java.util.UUID;

/**
 * Outbound port onto the {@code EntityObject} an {@code entityName}/{@code objectId} pair
 * identifies — read for guard evaluation, and the one channel through which base-state ever
 * writes an object's state attribute.
 *
 * <p>Unlike {@code app :: port}'s {@code EntityNameRegistry}, this port has no safe default
 * method: there is no reasonable stand-in payload or write for an entity object, so an
 * implementation is required rather than optional. See {@link UnavailableEntityObjectGateway} for
 * what happens with no implementation supplied — deliberately a loud failure rather than a silent
 * no-op, because the operation-layer use cases exist to be trusted with writes.
 *
 * <p>Nothing implements this yet: base-entity-backend has not implemented its operation layer
 * ({@code EntityObject} persistence) in code, only its knowledge-layer descriptor scaffold. The
 * port exists so the day that lands, wiring it here is the only integration step this module
 * needs.
 */
public interface EntityObjectGateway {

    /**
     * The object's current payload and version, for guard evaluation and for the optimistic-lock
     * comparison in {@code FireStateTransition}.
     *
     * @throws EntityObjectNotFoundException if no such object exists for {@code orgKey}/{@code
     *                                        entityName}/{@code objectId}
     */
    EntityObjectSnapshot findObject(String orgKey, String entityName, UUID objectId);

    /**
     * Writes {@code newValue} to the attribute named {@code attributeKey} on the object, and only
     * that attribute — the same "PUT .../properties without a blocks field" discipline
     * {@code base-artifact} uses to avoid clobbering unrelated content. Must be a compare-and-swap
     * against {@code expectedVersion}, rejecting with {@link
     * com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException} rather than
     * silently overwriting a since-changed object.
     *
     * @return the object's version after this write — {@code FireStateTransition} reports it back
     *         as {@code TransitionResult.version}, since the gateway is the only party that knows
     *         the post-write value
     * @throws EntityObjectNotFoundException             if no such object exists
     * @throws com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException
     *                                                    if {@code expectedVersion} does not match
     *                                                    the object's current version
     */
    long updateStateAttribute(String orgKey, String entityName, UUID objectId,
                               String attributeKey, String newValue, long expectedVersion);
}
