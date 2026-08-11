/**
 * Base Document — structural persistence for wiki-style textual documents.
 *
 * <p>Owns document/block structure, widget references and their static props, declared
 * input/output ports, and opaque Tiptap JSON content. Deliberately knows nothing about
 * runtime data binding, base-entity, or base-rule beyond the shared {@code Severity} scale
 * (see {@code com.processpuzzle.document.usecase.DocumentValidationProblem}).
 */
package com.processpuzzle.document;
