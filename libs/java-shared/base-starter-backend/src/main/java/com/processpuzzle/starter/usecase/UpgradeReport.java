package com.processpuzzle.starter.usecase;

import java.util.List;

/**
 * What an upgrade did, or would do, or why it was refused. The definitions are written as drafts: the organization's
 * administrator reviews them and releases.
 *
 * @param fromVersion  the version the organization had installed; null when the starter is not installed
 * @param skippedKinds kinds the bundle brings that cannot be upgraded definition by definition yet; left as they are
 */
public record UpgradeReport(boolean dryRun, Status status, String starterId, String fromVersion, String toVersion,
                            List<Item> items, List<String> skippedKinds, List<ImportReport.Error> errors) {

    public enum Status {
        APPLIED, WOULD_APPLY, REJECTED
    }

    /**
     * ADD — new in the bundle. REPLACE — in the bundle and untouched in the organization. REMOVE — dropped from the
     * bundle and untouched. KEEP_CUSTOMIZED — the organization changed it (or made it itself): its version wins.
     * KEEP_DELETED — the organization deleted it; it is not brought back. KEEP_IN_USE — dropped from the bundle, but
     * something still depends on it.
     */
    public enum Action {
        ADD, REPLACE, REMOVE, KEEP_CUSTOMIZED, KEEP_DELETED, KEEP_IN_USE
    }

    /** @param note why a definition was kept, when there is something to say; otherwise null */
    public record Item(String kind, String key, Action action, String note) {
    }

    public UpgradeReport {
        items = items == null ? List.of() : List.copyOf(items);
        skippedKinds = skippedKinds == null ? List.of() : List.copyOf(skippedKinds);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static UpgradeReport rejected(boolean dryRun, String starterId, String fromVersion, String toVersion,
                                         List<ImportReport.Error> errors) {
        return new UpgradeReport(dryRun, Status.REJECTED, starterId, fromVersion, toVersion, List.of(), List.of(), errors);
    }

    public long count(Action action) {
        return items.stream().filter(item -> item.action() == action).count();
    }
}
