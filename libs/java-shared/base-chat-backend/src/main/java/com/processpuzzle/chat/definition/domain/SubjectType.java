package com.processpuzzle.chat.definition.domain;

import org.springframework.modulith.NamedInterface;

/**
 * What kind of object a conversation is about. The subject is referenced by type and id only; the host
 * application resolves it through an outbound port, so this module never learns what an entity is.
 *
 * <p>Exposed as {@code chat :: subject-type} — this one type, not the package — because the port's
 * adapter in the host has to name it.
 */
@NamedInterface("subject-type")
public enum SubjectType {

    /** An instance of a base-entity definition; {@code subjectDefinition} is the definition code. */
    ENTITY
}
