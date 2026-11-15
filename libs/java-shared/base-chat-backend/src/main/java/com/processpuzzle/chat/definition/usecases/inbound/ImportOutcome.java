package com.processpuzzle.chat.definition.usecases.inbound;

import java.util.List;

/**
 * What an import of chat definitions did.
 *
 * @param created number of definitions that did not exist
 * @param updated number of existing definitions that were replaced
 * @param errors  every problem found; when there is any, nothing was written
 * @param written the keys written, in file order
 */
public record ImportOutcome(int created, int updated, List<String> errors, List<String> written) {

    public static ImportOutcome rejected(List<String> errors) {
        return new ImportOutcome(0, 0, List.copyOf(errors), List.of());
    }
}
