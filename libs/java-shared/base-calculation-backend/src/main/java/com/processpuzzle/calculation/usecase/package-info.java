/**
 * The use cases of base-calculation: CRUD, validation and YAML import of calculation definitions; evaluating,
 * running and freezing calculations.
 *
 * <p>Exposed as the {@code usecase} named interface, because the host application asks
 * {@link com.processpuzzle.calculation.usecase.FindCalculationDefinition#storedAttributesOf} which attributes a
 * calculation owns, so base-entity's forms keep them read-only. The nested {@code exception} package is not
 * propagated and stays internal.
 */
@NamedInterface("usecase")
package com.processpuzzle.calculation.usecase;

import org.springframework.modulith.NamedInterface;
