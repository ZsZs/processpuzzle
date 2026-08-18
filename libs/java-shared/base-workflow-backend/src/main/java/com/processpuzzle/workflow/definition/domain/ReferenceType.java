package com.processpuzzle.workflow.definition.domain;

/**
 * The kind of resource a {@link TaskIOReference} points to. BASE_ENTITY references an entity
 * instance/definition in base-entity, DOCUMENT references a base-artifact document, WIDGET
 * references a widget registered in base-entity-frontend's WIDGET_REGISTRY. base-workflow never
 * resolves these itself — they are carried as opaque (type, refId) pairs for consumers that do own
 * those modules.
 */
public enum ReferenceType {
    BASE_ENTITY, DOCUMENT, WIDGET
}
