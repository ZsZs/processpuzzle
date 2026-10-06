package com.processpuzzle.starter.usecase;

/**
 * An import that wrote nothing, carrying the report that says why. The {@link Reason} picks the HTTP
 * status: a bundle over a size limit (413), an organization whose instance data the import would
 * orphan (409), and everything else (422).
 */
public class ImportRejectedException extends RuntimeException {

    public enum Reason {
        INVALID, TOO_LARGE, HOLDS_INSTANCE_DATA
    }

    private final transient ImportReport report;
    private final Reason reason;

    public ImportRejectedException(ImportReport report, Reason reason) {
        super(report.errors().isEmpty() ? "Import rejected" : report.errors().getFirst().message());
        this.report = report;
        this.reason = reason;
    }

    public ImportReport getReport() {
        return report;
    }

    public Reason getReason() {
        return reason;
    }
}
