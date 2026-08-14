package com.processpuzzle.app.domain;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.util.List;

/**
 * What an {@link AppRoute} renders — the domain counterpart of the contract's {@code RouteTarget}.
 *
 * <p>Flat rather than a sealed hierarchy, mirroring the contract: fields belonging to another
 * {@link Kind} are simply null. Which fields a kind requires is checked by
 * {@code AppDefinitionValidator}, not structurally, so a half-authored target round-trips through
 * the designer instead of failing to deserialize.
 *
 * <p>There is deliberately no {@code MODULE} kind. A module is mounted by the application through
 * {@link ModuleMount}, never reached through a route target — a MODULE target would let a route
 * point into a module that points at another module, which is the unbounded nesting this design
 * exists to prevent.
 *
 * @param kind which of the field groups below is meaningful
 * @param widgets WIDGETS only — rendered in the content outlet, in declaration order
 * @param documentSlug DOCUMENT only — {@code Document.slug} within this organization
 * @param entityName ENTITY only — {@code BaseEntityDescriptor.entityName}
 * @param entityMode ENTITY only — which generated screen to render
 * @param rsqlFilter ENTITY + LIST only — RSQL applied to the backing search
 */
public record RouteTarget(
        Kind kind,
        List<Widget> widgets,
        String documentSlug,
        String entityName,
        EntityMode entityMode,
        String rsqlFilter) {

    public RouteTarget {
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }

    public enum Kind {
        @JsonEnumDefaultValue
        WIDGETS,
        DOCUMENT,
        ENTITY
    }

    public enum EntityMode {
        @JsonEnumDefaultValue
        LIST,
        DETAILS
    }

    /** A widget-rendering target, the kind a freshly created route starts as. */
    public static RouteTarget ofWidgets(List<Widget> widgets) {
        return new RouteTarget(Kind.WIDGETS, widgets, null, null, null, null);
    }
}
