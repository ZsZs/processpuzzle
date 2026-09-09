package com.processpuzzle.state.usecase.port;

import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;

import java.util.List;
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
 * <p>Implemented by {@code BaseEntityObjectGateway} over base-entity's published {@code
 * EntityObjectAccess}. The port stays because the dependency is one-directional by design — base-state
 * knows base-entity, never the reverse — and because an application may substitute its own adapter.
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

    /**
     * Every object of {@code entityName}, for a pass over the whole governed population rather
     * than one object — see {@code GovernedStateConsistencyCheck}. A whole-type read, so it belongs
     * to startup work and not to a request path.
     *
     * @return the objects in no guaranteed order; empty if the type has no instances
     */
    List<EntityObjectSnapshot> findObjects(String orgKey, String entityName);
}
