package com.processpuzzle.workflow.definition.usecases.outbound;

import com.processpuzzle.shared.event.EventParameterType;

/**
 * One parameter an event of the catalog declares — what a workflow throwing the event fills from its context, by name
 * (docs/event-parameters.md).
 */
public record CatalogParameter(String name, EventParameterType type, String entityType, boolean required) {}
