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
    boolean required,
    /** A COMMENTS attribute — an append-only list of {@code {author, authorId?, createdAt, content}} entries. */
    boolean comments,
    /** An ADDRESS attribute — one postal address object, see the ADDRESS form control type. */
    boolean address
) {
    public EntityAttributeView(String code, ValueKindView valueKind, boolean multiValued, boolean embeddedComponent,
                               String linkedEntityType, boolean required) {
        this(code, valueKind, multiValued, embeddedComponent, linkedEntityType, required, false, false);
    }

    public EntityAttributeView(String code, ValueKindView valueKind, boolean multiValued, boolean embeddedComponent,
                               String linkedEntityType, boolean required, boolean comments) {
        this(code, valueKind, multiValued, embeddedComponent, linkedEntityType, required, comments, false);
    }

    public enum ValueKindView {
        TEXT, NUMBER, BOOLEAN, DATE, DATE_TIME, ENUM, REFERENCE
    }
}
