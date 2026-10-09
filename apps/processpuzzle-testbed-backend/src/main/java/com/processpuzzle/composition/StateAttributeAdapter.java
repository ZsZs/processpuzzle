package com.processpuzzle.composition;

import com.processpuzzle.baseentity.port.ManagedAttributesPort;
import com.processpuzzle.state.api.StateOperationApi;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tells base-entity which attribute of an entity type base-state owns: the {@code stateAttributeKey} of the
 * machine governing it. base-entity's ordinary update then keeps that attribute's stored value, which is
 * what makes base-state the only writer of an object's state — a transition is the one way to change it.
 */
@Component
public class StateAttributeAdapter implements ManagedAttributesPort {

    private final StateOperationApi stateOperations;

    public StateAttributeAdapter(StateOperationApi stateOperations) {
        this.stateOperations = stateOperations;
    }

    @Override
    public Set<String> managedAttributesOf(String orgKey, String entityDefinitionCode) {
        return stateOperations.stateAttributeKey(orgKey, entityDefinitionCode).map(Set::of).orElseGet(Set::of);
    }
}
