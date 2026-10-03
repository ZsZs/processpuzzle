package com.processpuzzle.ai.usecase.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port answering what this module needs to know about the subjects it recognizes, which are
 * objects of another feature — base-entity in this platform: does the entity type exist, is an
 * attribute a text attribute, does an object exist, and what is its identifier.
 *
 * <p>The adapter belongs in the composition root, never in this library: an adapter here would keep
 * the compile edge to base-entity that the port exists to avoid.
 *
 * <p>Every method defaults to permitting, as base-app's ports do — a library that cannot answer a
 * question must not answer "no". With no adapter, any entity type and object is accepted and no
 * registered identifier is known, so matching falls back to appearance alone.
 */
public interface SubjectDirectory {

    default boolean entityTypeExists(String orgKey, String entityName) {
        return true;
    }

    default boolean isTextAttribute(String orgKey, String entityName, String attributeKey) {
        return true;
    }

    default boolean subjectExists(String orgKey, String entityName, UUID objectId) {
        return true;
    }

    /** The subject's registered identifier: the value of {@code attributeKey}, when it is set. */
    default Optional<String> identifier(String orgKey, String entityName, UUID objectId, String attributeKey) {
        return Optional.empty();
    }

    /** The answer when nothing is wired. */
    SubjectDirectory PERMISSIVE = new SubjectDirectory() {
    };
}
