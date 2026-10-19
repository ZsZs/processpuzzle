package com.processpuzzle.core.definition;

import java.util.Set;

/**
 * A subject a feature publishes {@code PlatformEvent}s about that is <em>not</em> an entity type — a document, a
 * calculation run. Declared as a bean by the feature that publishes the facts, so the event catalog can name the
 * subject: an event definition's subject is otherwise required to be an entity type, and a release refuses one that
 * is not.
 *
 * @param subjectType the {@code subjectType} of the facts
 * @param attributes  the payload attributes every such fact carries — what a catalogued event may declare as its
 *                    parameters
 */
public record PlatformSubject(String subjectType, Set<String> attributes) {

    public PlatformSubject {
        attributes = attributes == null ? Set.of() : Set.copyOf(attributes);
    }
}
