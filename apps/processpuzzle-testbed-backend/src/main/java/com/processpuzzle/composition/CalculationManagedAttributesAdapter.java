package com.processpuzzle.composition;

import com.processpuzzle.baseentity.port.ManagedAttributesPort;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tells base-entity which attributes of an entity type a released calculation stores its results in — an order's
 * {@code total}. base-entity's ordinary update then keeps their stored value, so a form saved with an old total does
 * not overwrite the calculated one; the calculation is their only writer. The second {@link ManagedAttributesPort}
 * besides {@link StateAttributeAdapter}: base-entity manages what either names.
 */
@Component
public class CalculationManagedAttributesAdapter implements ManagedAttributesPort {

    private final FindCalculationDefinition calculations;

    public CalculationManagedAttributesAdapter(FindCalculationDefinition calculations) {
        this.calculations = calculations;
    }

    @Override
    public Set<String> managedAttributesOf(String orgKey, String entityDefinitionCode) {
        return calculations.storedAttributesOf(orgKey, entityDefinitionCode);
    }
}
