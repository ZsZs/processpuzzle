package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.ImportedItem;
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

    public record Item(String kind, String key, ImportedItem.Action action) {
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
        return items.stream().filter(item -> item.action() == ImportedItem.Action.CREATE).count();
    }

    public long updated() {
        return items.stream().filter(item -> item.action() == ImportedItem.Action.UPDATE).count();
    }
}
