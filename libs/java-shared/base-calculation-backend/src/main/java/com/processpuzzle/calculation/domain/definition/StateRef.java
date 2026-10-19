package com.processpuzzle.calculation.domain.definition;

/** An object of {@code entity} entering state {@code to}. */
public record StateRef(String entity, String to) {
}
