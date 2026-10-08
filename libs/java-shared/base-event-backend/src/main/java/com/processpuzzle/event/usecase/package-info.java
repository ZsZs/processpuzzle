/**
 * The use cases of the event catalog: CRUD and YAML import of {@code EventDefinition}s.
 *
 * <p>Exposed as the {@code usecase} named interface, because the host application asks
 * {@link com.processpuzzle.event.usecase.FindEventDefinition#exists} on base-workflow's behalf. The
 * nested {@code exception} package is not propagated and stays internal.
 */
@NamedInterface("usecase")
package com.processpuzzle.event.usecase;

import org.springframework.modulith.NamedInterface;
