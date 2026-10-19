package com.processpuzzle.event.usecase;

import java.util.Optional;

/**
 * Answers whether an entity type has an attribute — what a SYSTEM event's declared parameter is filled from, so a
 * parameter naming none would never be filled (docs/event-parameters.md, decision 4).
 *
 * <p>The entity types are base-entity's, which this module does not depend on: the host application supplies the
 * adapter. Without one, {@link #UNKNOWN} cannot tell, and the check is skipped rather than failed.
 */
public interface SubjectAttributePort {

    /** Knows no entity type. */
    SubjectAttributePort UNKNOWN = (orgKey, subjectType, attributeName) -> Optional.empty();

    /**
     * Whether the draft of {@code subjectType} has {@code attributeName}; empty when the adapter cannot tell — no such
     * entity type, or none it can read.
     */
    Optional<Boolean> hasAttribute(String orgKey, String subjectType, String attributeName);
}
