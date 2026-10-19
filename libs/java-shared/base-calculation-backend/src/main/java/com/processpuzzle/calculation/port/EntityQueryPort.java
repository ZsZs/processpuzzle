package com.processpuzzle.calculation.port;

import java.util.List;
import java.util.Optional;

/** Reads the objects a calculation's parameters and inputs name. Scoped to the organization {@code orgKey}. */
public interface EntityQueryPort {

    /** The object, empty when there is none of that id and entity in the organization. */
    Optional<EntityRecord> find(String orgKey, String entity, String id);

    /**
     * Every object of {@code entity} matching the RSQL filter — every object of it for a blank one. Unpaged: an
     * input is read whole.
     *
     * @throws IllegalArgumentException when the filter is not valid RSQL for the entity
     */
    List<EntityRecord> findWhere(String orgKey, String entity, String rsql);
}
