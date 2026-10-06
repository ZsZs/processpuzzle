package com.processpuzzle.starter.usecase;

import java.util.List;

/**
 * What an import did, or would do, or why it was refused.
 *
 * @param starterId null when the bundle was refused before its manifest could be read
 */
public record ImportReport(boolean dryRun, Status status, String starterId, String version,
                           List<Item> items, List<Error> errors) {

    public enum Status {
        APPLIED, WOULD_APPLY, REJECTED
    }

    /**
     * CREATE — new; UPDATE — existed before and the starter brings it again; DELETE — existed before
     * and the starter does not bring it. A starter replaces what the organization had, so an UPDATE
     * is a delete and a create underneath.
     */
    public enum Action {
        CREATE, UPDATE, DELETE
    }

    public record Item(String kind, String key, Action action) {
    }

    /** @param file the offending path inside the bundle, or null */
    public record Error(String file, String message) {
    }

    public ImportReport {
        items = items == null ? List.of() : List.copyOf(items);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ImportReport rejected(boolean dryRun, String starterId, String version, List<Error> errors) {
        return new ImportReport(dryRun, Status.REJECTED, starterId, version, List.of(), errors);
    }

    public long created() {
        return count(Action.CREATE);
    }

    public long updated() {
        return count(Action.UPDATE);
    }

    public long deleted() {
        return count(Action.DELETE);
    }

    private long count(Action action) {
        return items.stream().filter(item -> item.action() == action).count();
    }
}
