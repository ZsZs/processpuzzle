package com.processpuzzle.core.definition;

/**
 * One definition naming another: an app route rendering an entity, a rule whose context is an
 * entity, a workflow throwing an event.
 *
 * @param fromKind the referring definition's kind
 * @param fromKey  the referring definition's key
 * @param toKind   the referenced definition's kind
 * @param toKey    the referenced definition's key
 * @param optional whether the referring definition still works without its target: an app mounting a
 *                 module nobody has authored yet merely shows no routes there. An unresolved optional
 *                 reference warns; an unresolved required one refuses the release.
 */
public record DefinitionReference(String fromKind, String fromKey, String toKind, String toKey, boolean optional) {

    public static DefinitionReference required(String fromKind, String fromKey, String toKind, String toKey) {
        return new DefinitionReference(fromKind, fromKey, toKind, toKey, false);
    }

    public static DefinitionReference optional(String fromKind, String fromKey, String toKind, String toKey) {
        return new DefinitionReference(fromKind, fromKey, toKind, toKey, true);
    }
}
