package com.processpuzzle.baseentity.port;

import java.util.Set;

/**
 * Attributes of an entity type whose value another module owns — today, the attribute base-state keeps an
 * object's current state in. An ordinary update keeps their stored value whatever the submitted payload says,
 * so a form saved with a stale or tampered state cannot move an object past its state machine.
 *
 * <p>base-entity names no module here; the application's composition root supplies the adapter. Without one
 * nothing is managed, which is how base-entity behaves on its own.
 */
public interface ManagedAttributesPort {

    ManagedAttributesPort NONE = (orgKey, entityDefinitionCode) -> Set.of();

    Set<String> managedAttributesOf(String orgKey, String entityDefinitionCode);
}
