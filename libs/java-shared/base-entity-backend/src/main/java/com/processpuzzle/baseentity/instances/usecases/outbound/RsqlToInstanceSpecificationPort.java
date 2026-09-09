package com.processpuzzle.baseentity.instances.usecases.outbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import org.springframework.data.jpa.domain.Specification;

/**
 * Port for converting an RSQL query string into a JPA {@link Specification} for {@link EntityObject}.
 */
public interface RsqlToInstanceSpecificationPort {

    Specification<EntityObject> toSpecification(String rsql, String rootEntityDefinitionCode);
}
