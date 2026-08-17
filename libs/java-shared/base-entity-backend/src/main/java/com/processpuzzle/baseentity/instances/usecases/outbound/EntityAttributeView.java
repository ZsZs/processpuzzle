package com.processpuzzle.baseentity.instances.usecases.outbound;

/**
 * Read-only projection of a definition-module BaseEntityAttribute — deliberately NOT the
 * definition module's JPA entity itself. Returning that type here would leak a domain object
 * across the module boundary and defeat the point of EntityDefinitionLookupPort being a port.
 */
public record EntityAttributeView(
    String code,
    ValueKindView valueKind,
    boolean multiValued,
    boolean embeddedComponent,
    String linkedEntityType,
    boolean required
) {
    public enum ValueKindView {
        TEXT, NUMBER, BOOLEAN, DATE, DATE_TIME, ENUM, REFERENCE
    }
}
