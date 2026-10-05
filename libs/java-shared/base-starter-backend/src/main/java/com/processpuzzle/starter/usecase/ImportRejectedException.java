package com.processpuzzle.starter.usecase;

/**
 * An import that wrote nothing, carrying the report that says why. {@code tooLarge} separates a bundle
 * over a size limit (413) from one that is invalid (422).
 */
public class ImportRejectedException extends RuntimeException {

    private final transient ImportReport report;
    private final boolean tooLarge;

    public ImportRejectedException(ImportReport report, boolean tooLarge) {
        super(report.errors().isEmpty() ? "Import rejected" : report.errors().getFirst().message());
        this.report = report;
        this.tooLarge = tooLarge;
    }

    public ImportReport getReport() {
        return report;
    }

    public boolean isTooLarge() {
        return tooLarge;
    }
}
