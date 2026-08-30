package com.processpuzzle.workflow.definition.domain;

/**
 * How a {@link TaskUse#getDependsOn()} set is satisfied. {@code ALL} (the default) waits for every
 * named task to reach a terminal status; {@code ANY} waits for the first of them. Immaterial when
 * {@code dependsOn} is empty — such a task is eligible from workflow start either way.
 */
public enum JoinType {
    ALL, ANY
}
