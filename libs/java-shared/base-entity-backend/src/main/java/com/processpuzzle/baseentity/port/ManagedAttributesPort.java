package com.processpuzzle.baseentity.port;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Attributes of an entity type whose value another module owns — the attribute base-state keeps an object's
 * current state in, and the ones base-calculation stores a calculation's results in. An ordinary update keeps
 * their stored value whatever the submitted payload says, so a form saved with a stale or tampered state cannot
 * move an object past its state machine, nor overwrite a derived total.
 *
 * <p>base-entity names no module here; the application's composition root supplies the adapters — one per owning
 * module, and an attribute any of them names is managed. Without one nothing is managed, which is how base-entity
 * behaves on its own.
 */
public interface ManagedAttributesPort {

    ManagedAttributesPort NONE = (orgKey, entityDefinitionCode) -> Set.of();

    Set<String> managedAttributesOf(String orgKey, String entityDefinitionCode);

    /** Every attribute any of {@code ports} manages. */
    static ManagedAttributesPort unionOf(List<ManagedAttributesPort> ports) {
        if (ports.isEmpty()) {
            return NONE;
        }
        return (orgKey, entityDefinitionCode) -> {
            Set<String> managed = new LinkedHashSet<>();
            ports.forEach(port -> managed.addAll(port.managedAttributesOf(orgKey, entityDefinitionCode)));
            return managed;
        };
    }
}
