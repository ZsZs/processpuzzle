package com.processpuzzle.workflow.definition.domain;

/**
 * The kinds of thing an {@link ArtifactDefinition} can be. Names the kind, not the schema: every
 * value here describes an artifact, and this field says which kind of one it is.
 * {@link ArtifactDefinition#getArtifactTypeId()} then names the concrete document, entity or
 * widget.
 */
public enum ArtifactType {
    DOCUMENT, ENTITY, WIDGET
}
