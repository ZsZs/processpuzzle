package com.processpuzzle.core.definition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Imports the definitions of one kind from a bundle file into one organization.
 *
 * <p>Implemented by each feature library for its own definitions, in that library's own seed YAML
 * format, and collected by {@code base-starter-backend}. The importer runs every participant inside a
 * single transaction it owns, and a dry run is that same transaction rolled back. Two obligations
 * follow for an implementation:
 * <ul>
 *   <li>{@link #apply} must write through the transaction it is called in, and must not commit it;</li>
 *   <li>any side effect outside the database — an in-memory cache, an engine registration — must be
 *       deferred until after commit, or a dry run leaves it behind.</li>
 * </ul>
 */
public interface DefinitionImportParticipant {

    /** One of {@link DefinitionKinds}' kind constants. */
    String kind();

    /** Where this kind runs relative to the others; see {@link DefinitionKinds}' order constants. */
    int order();

    /**
     * Validates and writes the definitions of one file, all or nothing: either every definition in
     * the file is written, or none is and {@link ParticipantResult#errors()} says why.
     *
     * @param fileName the file's path inside the bundle, for error messages
     */
    ParticipantResult apply(String orgKey, String fileName, byte[] yaml);

    /**
     * Deletes every definition of this kind in the organization, the first step of installing a
     * starter: a starter replaces what the organization had rather than merging into it. Called in
     * reverse {@link #order()}, inside the same transaction as {@link #apply}, and only once the
     * importer has made sure no {@link InstanceDataProbe} reports data that could still refer to a
     * definition.
     *
     * @return the keys deleted
     */
    List<String> removeAll(String orgKey);

    /**
     * The canonical JSON of each key's current state in the database, for telling an untouched
     * definition from a customized one. Must be stable: the same stored definition always yields the
     * same string, so volatile fields such as ids, versions and audit timestamps are left out. A key
     * that does not exist is absent from the result.
     */
    Map<String, String> fingerprints(String orgKey, Set<String> keys);
}
