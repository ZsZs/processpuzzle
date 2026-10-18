package com.processpuzzle.baseentity.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read and single-attribute write access to one {@code EntityObject}, for a module that owns an
 * attribute of it without owning the object.
 *
 * <p>Every method is scoped to the organization {@code orgKey}: an object another organization owns is
 * not found, exactly like one that does not exist.
 *
 * <p>base-state is the caller, through its {@code EntityObjectGateway} adapter: it is the only
 * legitimate writer of the attribute a state machine's {@code stateAttributeKey} names, and this is
 * the channel it writes through.
 *
 * <p>base-calculation is the other, through the host application's adapters: it reads a calculation's inputs
 * with {@link #findWhere} and writes the derived values it owns with {@link #updateAttributes} and
 * {@link #upsertByKey}.
 */
public interface EntityObjectAccess {

    /**
     * @throws EntityObjectAccessException.NotFound if no object with {@code objectId} exists, or if
     *         it exists but is not of type {@code entityDefinitionCode} — an id from the wrong type
     *         is a caller error, not an empty result
     */
    EntityObjectView find(String orgKey, String entityDefinitionCode, UUID objectId);

    /**
     * Every object of one type, for a caller that has to reason about the whole population rather
     * than one object — base-state's startup consistency check over the entities its machines
     * govern.
     *
     * <p>Unpaged and unfiltered, because the caller's question ("does any object of this type
     * disagree with its state machine?") is not answerable from a page. That makes it a whole-table
     * read of one entity type: acceptable for a once-per-boot pass, not for a request path.
     *
     * @return the objects in no guaranteed order; empty if the type has no instances or is not a
     *         known definition code — an unknown type has no objects, which is not a caller error
     *         the way an id of the wrong type is
     */
    List<EntityObjectView> findAll(String orgKey, String entityDefinitionCode);

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
    long updateAttribute(String orgKey, String entityDefinitionCode, UUID objectId, String attributeCode,
                         String value, long expectedVersion);

    /**
     * The objects of one type an RSQL filter selects — every object of it for a blank filter. Unpaged, like
     * {@link #findAll}: the caller reads its whole input.
     *
     * @throws IllegalArgumentException if the filter is not valid RSQL, or names an attribute the type lacks
     */
    List<EntityObjectView> findWhere(String orgKey, String entityDefinitionCode, String rsql);

    /**
     * Writes {@code values} — typed: numbers stay numbers — and leaves every other key of the payload as it was,
     * provided the object is still at {@code expectedVersion}.
     *
     * <p>Unlike {@link #updateAttribute} the version does <em>not</em> move. These are derived values of attributes
     * the caller owns, which an ordinary update keeps as stored ({@code ManagedAttributesPort}); a moved version would
     * only make the user who just saved the object fail their next save with a conflict. No payload validation and no
     * event, for the reasons {@link #updateAttribute} gives — and an event would trigger the calculation that wrote.
     *
     * @throws EntityObjectAccessException.NotFound        if no such object exists
     * @throws EntityObjectAccessException.VersionConflict  if the object is no longer at {@code expectedVersion}
     */
    void updateAttributes(String orgKey, String entityDefinitionCode, UUID objectId, Map<String, Object> values,
                          long expectedVersion);

    /**
     * Writes {@code values} onto the object of {@code entityDefinitionCode} whose attributes equal {@code key} — the
     * first, should there be several — or creates one with {@code values} as its payload. Neither validates nor
     * publishes an event; an existing object keeps its version, as with {@link #updateAttributes}.
     *
     * <p>The match reads every object of the type: fine for a result table of one calculation, not a request path.
     *
     * @return whether an object was created
     * @throws EntityObjectAccessException.NotFound if {@code entityDefinitionCode} is no entity type of the organization
     */
    boolean upsertByKey(String orgKey, String entityDefinitionCode, Map<String, Object> key, Map<String, Object> values);
}
