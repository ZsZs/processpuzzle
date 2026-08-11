/**
 * Base Document — structural persistence for wiki-style textual documents.
 *
 * <p>Owns document/block structure, widget references and their static props, declared
 * input/output ports, opaque Tiptap JSON content, per-locale translations and the editorial
 * draft/published split. Deliberately knows nothing about runtime data binding or base-entity.
 *
 * <p>The declared dependencies are the two open infrastructure modules plus one named interface:
 *
 * <ul>
 *   <li>{@code core}, {@code shared} — infrastructure, open to every module.
 *   <li>{@code rule :: domain} — {@code Severity} alone, because
 *       {@code com.processpuzzle.document.usecase.DocumentValidationProblem} grades validation
 *       problems on base-rule's scale rather than inventing a second one. Nothing else of
 *       base-rule is reachable from here: documents are not evaluated against rules.
 * </ul>
 *
 * <p>The annotation matters more than it looks. Without it, Spring Modulith still infers a module
 * from this package but enforces no allow-list, so {@code BaseDocumentModularityTests} passed
 * while the {@code Severity} edge above was entirely undeclared. With it, a reach into another
 * feature's internals fails the build rather than review.
 */
@ApplicationModule(displayName = "Base Document", allowedDependencies = {"core", "shared", "rule :: domain"})
package com.processpuzzle.document;

import org.springframework.modulith.ApplicationModule;
