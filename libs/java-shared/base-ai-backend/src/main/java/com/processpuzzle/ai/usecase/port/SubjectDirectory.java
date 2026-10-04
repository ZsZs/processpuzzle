package com.processpuzzle.ai.usecase.port;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port answering what this module needs to know about the subjects it recognizes, which are
 * objects of another feature — base-entity in this platform: does the entity type exist, what kind an
 * attribute is, does an object exist, what is its identifier, and which photos of it are there.
 *
 * <p>The adapter belongs in the composition root, never in this library: an adapter here would keep
 * the compile edge to base-entity that the port exists to avoid.
 *
 * <p>The questions about types and objects default to permitting, as base-app's ports do — a library
 * that cannot answer a question must not answer "no". With no adapter, any entity type and object is
 * accepted, no registered identifier is known and no subject has photos, so nothing gets enrolled.
 */
public interface SubjectDirectory {

    default boolean entityTypeExists(String orgKey, String entityName) {
        return true;
    }

    default boolean isTextAttribute(String orgKey, String entityName, String attributeKey) {
        return true;
    }

    /** Whether {@code attributeKey} can hold artifacts — the photos a gallery is enrolled from. */
    default boolean isPhotoAttribute(String orgKey, String entityName, String attributeKey) {
        return true;
    }

    default boolean subjectExists(String orgKey, String entityName, UUID objectId) {
        return true;
    }

    /** The subject's registered identifier: the value of {@code attributeKey}, when it is set. */
    default Optional<String> identifier(String orgKey, String entityName, UUID objectId, String attributeKey) {
        return Optional.empty();
    }

    /**
     * The image artifacts currently held in {@code attributeKey} of the subject, in attribute order.
     * Artifacts that are not images are left out. Empty for an unknown subject.
     */
    default List<SubjectPhoto> photos(String orgKey, String entityName, UUID objectId, String attributeKey) {
        return List.of();
    }

    /** Signed GET URL of a photo {@link #photos} reported, for a browser. */
    default String photoUrl(String photoRef, Duration expiry) {
        throw new UnsupportedOperationException("no subject photos are available");
    }

    /** Signed GET URL of a photo {@link #photos} reported, reachable from the infrastructure network. */
    default String internalPhotoUrl(String photoRef, Duration expiry) {
        throw new UnsupportedOperationException("no subject photos are available");
    }

    /**
     * One photo of a subject.
     *
     * @param photoRef stable and unique among the subject's photos; opaque to this module, which only
     *                 compares it and hands it back
     */
    record SubjectPhoto(String photoRef, String contentType) {
    }

    /** The answer when nothing is wired. */
    SubjectDirectory PERMISSIVE = new SubjectDirectory() {
    };
}
