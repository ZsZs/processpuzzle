/**
 * One class per definition-layer operation: CRUD for process/role/task/tool definitions plus
 * SPEM YAML import/export. Role and task mutation goes through {@code ProcessDefinition}, the
 * aggregate root — see its Javadoc.
 */
package com.processpuzzle.workflow.definition.usecases.inbound;
