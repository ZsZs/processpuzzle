package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.instances.domain.MeasureValues;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Puts every QUANTITY, MONEY and DIMENSIONS value of a payload — at every level of its embedded components — into its
 * stored form: canonical amounts and the derived numbers a query compares, see {@link MeasureValues}.
 *
 * <p>Runs after validation, so every measure it meets is valid; a value it cannot read as an object is left as it is.
 * A level whose definition the lookup cannot find is left as it is too, like {@link EmbeddedItemIdentity}.
 */
public final class MeasureNormalizer {

    private MeasureNormalizer() {
    }

    /**
     * The payload with its measures normalized. The input is not changed.
     *
     * @param definitions the definition of a component type, by its code
     */
    public static Map<String, Object> withDerivedValues(EntityDefinitionView definition, Map<String, Object> payload,
                                                        Function<String, Optional<EntityDefinitionView>> definitions) {
        if (payload == null || definition == null) {
            return payload;
        }
        Map<String, Object> result = new LinkedHashMap<>(payload);
        for (EntityAttributeView attribute : definition.attributes()) {
            Object value = payload.get(attribute.code());
            if (value == null) {
                continue;
            }
            if (attribute.measure() != null && value instanceof Map<?, ?>) {
                result.put(attribute.code(), MeasureValues.normalized(attribute.measure(), value));
            } else if (attribute.embeddedComponent()) {
                result.put(attribute.code(), embedded(attribute, value, definitions));
            }
        }
        return result;
    }

    /** One component row, or every row of a multi-valued collection, normalized against the component's definition. */
    @SuppressWarnings("unchecked")
    private static Object embedded(EntityAttributeView attribute, Object value, Function<String, Optional<EntityDefinitionView>> definitions) {
        EntityDefinitionView component = attribute.linkedEntityType() == null ? null : definitions.apply(attribute.linkedEntityType()).orElse(null);
        if (component == null) {
            return value;
        }
        if (value instanceof Map<?, ?> row) {
            return withDerivedValues(component, (Map<String, Object>) row, definitions);
        }
        if (value instanceof List<?> rows) {
            List<Object> normalized = new ArrayList<>(rows.size());
            for (Object row : rows) {
                normalized.add(row instanceof Map<?, ?> map ? withDerivedValues(component, (Map<String, Object>) map, definitions) : row);
            }
            return normalized;
        }
        return value;
    }
}
