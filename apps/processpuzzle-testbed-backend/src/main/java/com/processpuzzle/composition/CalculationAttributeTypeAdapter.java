package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.calculation.port.AttributeType;
import com.processpuzzle.calculation.port.EntityAttributeTypePort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Types the attributes base-calculation reads from base-entity's released definitions: a {@code NUMBER} is a decimal,
 * a {@code DATE} and a {@code DATE_TIME} are dates for FEEL; every other kind is passed as stored.
 */
@Component
public class CalculationAttributeTypeAdapter implements EntityAttributeTypePort {

    private final EntityAttributeQuery attributes;

    public CalculationAttributeTypeAdapter(EntityAttributeQuery attributes) {
        this.attributes = attributes;
    }

    @Override
    public Optional<AttributeType> typeOf(String orgKey, String entity, String attribute) {
        return attributes.attributeKind(orgKey, entity, attribute).map(kind -> switch (kind) {
            case TEXT, ENUM -> AttributeType.TEXT;
            case NUMBER -> AttributeType.NUMBER;
            case BOOLEAN -> AttributeType.BOOLEAN;
            case DATE -> AttributeType.DATE;
            case DATE_TIME -> AttributeType.DATE_TIME;
            case REFERENCE -> AttributeType.OTHER;
        });
    }
}
