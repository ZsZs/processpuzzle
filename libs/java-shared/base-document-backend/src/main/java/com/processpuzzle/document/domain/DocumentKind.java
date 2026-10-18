package com.processpuzzle.document.domain;

/**
 * What a document's entity input ports mean — the one difference between a document and a report
 * template; content, editor and publication are shared. See docs/document-vs-report-template.md.
 */
public enum DocumentKind {

    /** One output: entity ports are bound when it is authored, or by the page that hosts it. */
    DOCUMENT,

    /** One output per run: entity ports are parameters, supplied each time it is rendered. */
    REPORT_TEMPLATE
}
