package com.processpuzzle.calculation.domain.definition;

/**
 * A named input: the rows a calculation reads. Either a query, {@code { entity, where }} with an RSQL filter in
 * which {@code ${param}} and {@code ${param.attribute}} are replaced by parameter values, or the rows embedded in an
 * entity parameter's object, {@code { from: <parameter>, path: <attribute> }} — how an order's lines are reached.
 *
 * @param entity the entity definition code queried; with {@code from}, optionally the embedded rows' own entity
 *               code, which types their attributes
 */
public record InputSpec(String entity, String where, String from, String path) {

    public boolean isEmbedded() {
        return from != null && !from.isBlank();
    }
}
