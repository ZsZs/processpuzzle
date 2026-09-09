/**
 * The two ports the definition layer depends on.
 *
 * <p>{@link com.processpuzzle.workflow.definition.usecases.outbound.ActiveWorkflowInstanceExistencePort}
 * answers whether a workflow definition still has active instances, needed to guard
 * {@code DeleteWorkflowUseCase} without the definition layer depending on the execution layer's
 * repositories directly.
 *
 * <p>{@link com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort} projects the
 * organization's role catalog into the identity provider's realm roles. It is the only outbound
 * <em>write</em> in this library, and the only port here whose adapter ships with it: the
 * ActiveWorkflowInstance one is satisfied inside the same library, whereas a deployment with its own
 * tenant registry is expected to replace the role directory adapter.
 */
package com.processpuzzle.workflow.definition.usecases.outbound;
