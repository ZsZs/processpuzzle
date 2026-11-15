package com.processpuzzle.chat.definition.domain;

/**
 * How many conversations a definition allows per subject.
 */
public enum Multiplicity {

    /** At most one conversation per subject. */
    SINGLE,

    /** Any number of titled topics per subject, opened by holders of a create role. */
    MANY
}
