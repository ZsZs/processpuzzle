package com.processpuzzle.calculation.domain.definition;

/**
 * DMN hit policies of a {@link DecisionTable}: the first matching rule, exactly one matching rule, any number of
 * matching rules that agree, or the list of every matching rule's output.
 */
public enum HitPolicy {
    FIRST, UNIQUE, ANY, COLLECT
}
