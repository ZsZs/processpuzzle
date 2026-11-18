package com.processpuzzle.chat.definition.usecases.inbound;

import java.util.List;

/**
 * What an import of chat definitions did.
 *
 * @param created number of definitions that did not exist
 * @param updated number of existing definitions that were replaced
 * @param errors  every problem found; when there is any, nothing was written
 * @param written the keys written, in file order
 * @param updatedKeys those of the written keys that replaced an existing draft
 */
public record ImportOutcome(int created, int updated, List<String> errors, List<String> written, List<String> updatedKeys) {

    public ImportOutcome(int created, int updated, List<String> errors, List<String> written) {
        this(created, updated, errors, written, List.of());
    }

    public static ImportOutcome rejected(List<String> errors) {
        return new ImportOutcome(0, 0, List.copyOf(errors), List.of());
    }
}
