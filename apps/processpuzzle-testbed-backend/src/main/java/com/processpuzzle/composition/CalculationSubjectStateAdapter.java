package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.calculation.port.SubjectStatePort;
import com.processpuzzle.state.api.StateOperationApi;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Tells base-calculation which objects are in a state: base-state names the attribute that holds an entity's state,
 * base-entity selects the objects whose attribute has the value.
 *
 * <p>A whole read of the entity type, filtered here rather than through RSQL — whose jsonpath predicates are
 * PostgreSQL's alone. Acceptable for its one caller, the catch-up after a release, which is rare.
 */
@Component
public class CalculationSubjectStateAdapter implements SubjectStatePort {

    private final StateOperationApi stateOperations;
    private final EntityObjectAccess entities;

    public CalculationSubjectStateAdapter(StateOperationApi stateOperations, EntityObjectAccess entities) {
        this.stateOperations = stateOperations;
        this.entities = entities;
    }

    @Override
    public List<String> objectsInState(String orgKey, String entity, String state) {
        return stateOperations.stateAttributeKey(orgKey, entity)
                .map(attribute -> entities.findAll(orgKey, entity).stream()
                        .filter(object -> state.equals(String.valueOf(object.payload().get(attribute))))
                        .map(object -> object.id().toString()).toList())
                .orElseGet(List::of);
    }
}
