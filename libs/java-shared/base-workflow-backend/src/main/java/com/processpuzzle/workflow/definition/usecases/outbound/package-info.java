/**
 * The ports the definition layer depends on.
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
 *
 * <p>{@link com.processpuzzle.workflow.definition.usecases.outbound.EventCatalogPort} answers whether
 * the event a TRIGGERING_EVENT start event or an intermediate event names exists in the organization's
 * event catalog, and of which kind it is. The host application supplies it from base-event; without one,
 * {@link com.processpuzzle.workflow.definition.usecases.outbound.PermitAllEventCatalogPort} accepts any
 * name. It also says which state change a SYSTEM event stands for ({@link
 * com.processpuzzle.workflow.definition.usecases.outbound.CatalogStateChange}), which the release reads.
 *
 * <p>{@link com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort} reads the draft state
 * machine an artifact follows, for the release's check that a workflow handles every transition its tasks leave
 * open. Its adapter ships with this library and reads base-state; without base-state,
 * {@link com.processpuzzle.workflow.definition.usecases.outbound.NoStateMachineCatalogPort} knows no machine.
 *
 * <p>That is why this package is the {@code definition-port} named interface: a host application
 * implements these.
 */
@NamedInterface("definition-port")
package com.processpuzzle.workflow.definition.usecases.outbound;

import org.springframework.modulith.NamedInterface;
