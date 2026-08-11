package com.processpuzzle.document.domain;

import java.io.Serializable;

/** Data this document can emit to its hosting context. See {@link DocumentInputPort}. */
public record DocumentOutputPort(
        String name,
        PortType type,
        String description,
        String entityType,
        AttributeVisibility attributeVisibility) implements Serializable {

    private static final long serialVersionUID = 1L;
}
