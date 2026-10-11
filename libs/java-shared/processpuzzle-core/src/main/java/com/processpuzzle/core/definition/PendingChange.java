package com.processpuzzle.core.definition;

/**
 * One definition whose draft differs from its released row.
 *
 * @param kind        one of {@link DefinitionKinds}' kind constants
 * @param key         the definition's key within its kind and organization
 * @param change      what releasing would do to the released row
 * @param fingerprint the canonical JSON of the draft, as {@link CanonicalJson} writes it; {@code null}
 *                    for {@link Change#REMOVED}, which has no draft
 */
public record PendingChange(String kind, String key, Change change, String fingerprint) {

    public enum Change {
        ADDED, CHANGED, REMOVED
    }

    /**
     * Compares a draft with its released row by their fingerprints.
     *
     * @param draft    the draft's fingerprint, {@code null} when there is no draft
     * @param released the released row's fingerprint, {@code null} when nothing is released
     * @return the pending change, or {@code null} when the two are the same or neither exists
     */
    public static PendingChange between(String kind, String key, String draft, String released) {
        if (draft == null) {
            return released == null ? null : new PendingChange(kind, key, Change.REMOVED, null);
        }
        if (released == null) {
            return new PendingChange(kind, key, Change.ADDED, draft);
        }
        return draft.equals(released) ? null : new PendingChange(kind, key, Change.CHANGED, draft);
    }
}
