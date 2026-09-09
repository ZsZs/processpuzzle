package com.processpuzzle.document.domain;

import java.io.Serializable;

/**
 * Data this document expects to receive from its hosting context (e.g. base-app). Declares a
 * contract, not a value — resolving an actual binding at runtime is entirely a frontend
 * concern, base-document only records that the port exists.
 *
 * @param entityType         ENTITY_REF / ENTITY_COLLECTION only
 * @param attributeVisibility ENTITY_REF / ENTITY_COLLECTION only — optional default projection
 * @param defaultRsqlFilter  ENTITY_COLLECTION only — fallback filter when the host binds
 *                           this port without supplying its own
 */
public record DocumentInputPort(
        String name,
        PortType type,
        boolean required,
        String description,
        Object defaultValue,
        String entityType,
        AttributeVisibility attributeVisibility,
        String defaultRsqlFilter) implements Serializable {

    private static final long serialVersionUID = 1L;
}
