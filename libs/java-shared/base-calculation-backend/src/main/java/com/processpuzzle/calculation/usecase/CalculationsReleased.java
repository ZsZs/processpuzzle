package com.processpuzzle.calculation.usecase;

import java.util.Set;

/** The calculations of these ids were released in {@code orgKey}. Module-internal. */
public record CalculationsReleased(String orgKey, Set<String> calculationIds) {
}
