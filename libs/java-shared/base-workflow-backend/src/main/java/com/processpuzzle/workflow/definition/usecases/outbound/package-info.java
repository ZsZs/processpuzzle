/**
 * The one port the definition layer depends on: whether a workflow definition still has active
 * instances, needed to guard {@code DeleteWorkflowUseCase} without the definition layer
 * depending on the execution layer's repositories directly.
 */
package com.processpuzzle.workflow.definition.usecases.outbound;
