package com.processpuzzle.artifact.domain;

import java.io.Serializable;

/** Data this artifact can emit to its hosting context. See {@link ArtifactInputPort}. */
public record ArtifactOutputPort(
        String name,
        PortType type,
        String description,
        String entityType,
        AttributeVisibility attributeVisibility) implements Serializable {

    private static final long serialVersionUID = 1L;
}
