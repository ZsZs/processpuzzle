package com.processpuzzle.core.definition;

/**
 * Loads the records of one bundle file — the seed data of a starter or common — into one organization.
 *
 * <p>Implemented by the feature that owns the records, and collected by {@code base-starter-backend}, which calls it for
 * every {@code records} file after the definitions are written, so the records' definitions and rules exist. Records
 * are additive: a record a stored one already matches is left as it is, and the rest are created.
 *
 * <p>An implementation must write through the transaction it is called in and must not commit it: a refused file makes
 * the importer roll the whole bundle back.
 */
public interface RecordImportParticipant {

    /**
     * Loads the records of one file, all or nothing: when any record is refused, the result carries the reasons and the
     * importer rolls back. Items are the records created; records that were already present are not reported.
     *
     * @param fileName the file's path inside the bundle, for error messages
     */
    ParticipantResult load(String orgKey, String fileName, byte[] yaml);
}
