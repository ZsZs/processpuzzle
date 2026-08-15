package com.processpuzzle.baseentity.instances.usecases.outbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import org.springframework.data.jpa.domain.Specification;

/**
 * TODO: this port — and its current adapter implementation — exist only because
 * processpuzzle-core's RsqlSpecificationBuilder shape wasn't available when this module was
 * built. Once its API is confirmed, SearchEntityInstancesUseCase should very likely depend on
 * (a thin wrapper around) that core class directly instead of this bespoke port, and this
 * interface + its adapter should be deleted rather than kept as a parallel abstraction.
 */
public interface RsqlToInstanceSpecificationPort {

    Specification<EntityObject> toSpecification(String rsql, String rootEntityDefinitionCode);
}
