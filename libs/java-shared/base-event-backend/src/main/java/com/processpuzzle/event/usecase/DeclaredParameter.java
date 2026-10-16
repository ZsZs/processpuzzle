package com.processpuzzle.event.usecase;

import com.processpuzzle.shared.event.EventParameterType;

/**
 * One parameter an event definition declares — what a consumer outside this module asks of the catalog, without
 * reading the definition itself.
 */
public record DeclaredParameter(String name, EventParameterType type, String entityType, boolean required) {}
