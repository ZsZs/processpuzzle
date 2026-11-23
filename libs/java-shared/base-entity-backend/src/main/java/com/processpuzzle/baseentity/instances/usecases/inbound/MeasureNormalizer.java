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
        return normalize(definition, payload, definitions, false);
    }

    /**
     * Like {@link #withDerivedValues}, and also puts a bare number or amount in a measure attribute into its measure object, in the
     * attribute's default unit or currency. A value stored before the attribute became a measure is a bare number; this is how it
     * is brought up to the measure it is now. A measure object already in its stored form is left as it is, so a payload that is
     * up to date comes back equal to its input. A value that is neither is left as it is too.
     */
    public static Map<String, Object> withMeasureObjects(EntityDefinitionView definition, Map<String, Object> payload,
                                                         Function<String, Optional<EntityDefinitionView>> definitions) {
        return normalize(definition, payload, definitions, true);
    }

    private static Map<String, Object> normalize(EntityDefinitionView definition, Map<String, Object> payload,
                                                 Function<String, Optional<EntityDefinitionView>> definitions, boolean adoptBare) {
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
                if (!adoptBare) {
                    result.put(attribute.code(), MeasureValues.normalized(attribute.measure(), value));
                }
            } else if (attribute.measure() != null && adoptBare) {
                adoptBareValue(attribute, value).ifPresent(measure -> result.put(attribute.code(), measure));
            } else if (attribute.embeddedComponent()) {
                result.put(attribute.code(), embedded(attribute, value, definitions, adoptBare));
            }
        }
        return result;
    }

    /** The measure object a bare value stands for, or empty when the value cannot be read as a measure. */
    private static Optional<Map<String, Object>> adoptBareValue(EntityAttributeView attribute, Object value) {
        try {
            return Optional.of(MeasureValues.forWrite(attribute.measure(), value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** One component row, or every row of a multi-valued collection, normalized against the component's definition. */
    @SuppressWarnings("unchecked")
    private static Object embedded(EntityAttributeView attribute, Object value,
                                   Function<String, Optional<EntityDefinitionView>> definitions, boolean adoptBare) {
        EntityDefinitionView component = attribute.linkedEntityType() == null ? null : definitions.apply(attribute.linkedEntityType()).orElse(null);
        if (component == null) {
            return value;
        }
        if (value instanceof Map<?, ?> row) {
            return normalize(component, (Map<String, Object>) row, definitions, adoptBare);
        }
        if (value instanceof List<?> rows) {
            List<Object> normalized = new ArrayList<>(rows.size());
            for (Object row : rows) {
                normalized.add(row instanceof Map<?, ?> map ? normalize(component, (Map<String, Object>) map, definitions, adoptBare) : row);
            }
            return normalized;
        }
        return value;
    }
}
