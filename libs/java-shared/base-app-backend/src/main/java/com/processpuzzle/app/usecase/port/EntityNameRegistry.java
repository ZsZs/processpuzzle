package com.processpuzzle.app.usecase.port;

/**
 * Outbound port answering whether an organization has an entity descriptor of a given name, used to
 * validate the {@code entityName} that {@code entity-grid} and {@code entity-form} widgets
 * reference.
 *
 * <p>Nothing implements it yet: {@code base-entity} has no backend half — its contract
 * ({@code base-entity-api.yaml}) is still an empty stub, and descriptors live in the frontend. The
 * port exists so the check has a home the day a registry appears, and defaults to accepting every
 * name so the validator does not reject perfectly good definitions in the meantime.
 */
public interface EntityNameRegistry {

    /** Whether {@code entityName} is a descriptor registered for {@code orgKey}. */
    default boolean isKnownEntity(String orgKey, String entityName) {
        return true;
    }
}
