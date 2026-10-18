package com.processpuzzle.calculation.port;

import java.util.Optional;

/** The types of the attributes a calculation reads, so stored text becomes a FEEL date or number. */
public interface EntityAttributeTypePort {

    /** Every value passed as stored, numbers excepted; what a host without an entity catalog gets. */
    EntityAttributeTypePort UNKNOWN = (orgKey, entity, attribute) -> Optional.empty();

    /** The attribute's type, empty for an unknown entity or attribute. */
    Optional<AttributeType> typeOf(String orgKey, String entity, String attribute);
}
