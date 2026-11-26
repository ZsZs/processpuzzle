package com.processpuzzle.core.definition;

/**
 * Where an organization's seed records wait for its next release. Implemented by base-release, for an install or an
 * upgrade of a starter that brings {@code records}: the definitions it writes are drafts, and a record is validated
 * against — and stored under — the <em>released</em> definition of its type, so a record of a type the same bundle
 * creates or changes can only be loaded once that type is released. The release loads the queued files, through the
 * {@link RecordImportParticipant}, after every kind has released, in the same transaction: a refused record refuses
 * the release.
 *
 * <p>Joins the caller's transaction, so a dry run or a refused install leaves nothing queued.
 */
public interface PendingRecordQueue {

    /**
     * Appends one records file to the organization's pending ones, after those already queued.
     *
     * @param fileName the file's path inside the bundle, for messages
     * @param yaml     the file's content
     */
    void enqueue(String orgKey, String fileName, byte[] yaml);
}
