/**
 * Base Artifact — structural persistence for wiki-style textual artifacts.
 *
 * <p>Owns artifact/block structure, widget references and their static props, declared
 * input/output ports, and opaque Tiptap JSON content. Deliberately knows nothing about
 * runtime data binding, base-entity, or base-rule beyond the shared {@code Severity} scale
 * (see {@code com.processpuzzle.artifact.usecase.ArtifactValidationProblem}).
 */
package com.processpuzzle.artifact;
