package com.processpuzzle.core.definition;

/**
 * One thing wrong with a draft a client wrote or asked to validate: where it is, and what to fix.
 *
 * @param severity {@link Severity#ERROR} refuses the draft; {@link Severity#WARNING} says it is saved but needs a look
 * @param path     the JSON path of the offending value, such as {@code attributes[2].valueKind}, or empty when the whole
 *                 draft is at fault
 * @param text     a message the client can show, or act on when it repairs the draft
 */
public record AuthoringIssue(Severity severity, String path, String text) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static AuthoringIssue error(String path, String text) {
        return new AuthoringIssue(Severity.ERROR, path, text);
    }
}
