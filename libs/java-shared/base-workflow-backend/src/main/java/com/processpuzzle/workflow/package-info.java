/**
 * Base Workflow: SPEM-inspired orchestrator that interprets workflow definitions (roles,
 * artifacts, tasks, tools) to execute and monitor long-running workflow instances.
 *
 * <p>base-workflow is a pure orchestrator, per the API contract: rule evaluation is delegated to
 * Base Rule, state machine transitions to Base State, and entity/document/widget data shape to
 * base-entity / base-artifact. Those two are the only modules this one is allowed to reach into
 * directly, and only through their published named interfaces:
 *
 * <ul>
 *   <li>{@code rule :: usecase} / {@code rule :: domain} — {@link com.processpuzzle.rule.usecase.EvaluateObject}
 *       evaluates a task's precondition/postcondition rules. Injected via {@code ObjectProvider}
 *       (see {@code execution.adapters.outbound.rule.BaseRuleEvaluationAdapter}), so a host
 *       application that wires Base Workflow without Base Rule still runs — preconditions/postconditions
 *       are simply treated as always-satisfied.
 *   <li>{@code state :: domain} / {@code state :: operations} — read-only visibility into
 *       state/transition definitions and the published state-operation API used by the
 *       {@code EntityStateGateway} adapter.
 * </ul>
 *
 * <p>base-workflow never depends on base-entity or base-artifact directly — neither currently
 * exposes a named interface for this. Instead it defines its own outbound ports
 * ({@code RoleMembershipPort}, entity/document/widget references are carried as opaque IDs) that a
 * host application implements, the same pattern base-app uses for {@code EntityNameRegistry} /
 * {@code OrganizationAccessPolicy}.
 *
 * <p>{@code shared} is needed because the generated {@code workflow.api} returns the shared
 * {@code ImportResult} / {@code ErrorResponse} models (see api-contracts schemaMappings).
 *
 * <h2>The one thing this module writes outside itself</h2>
 *
 * <p>base-workflow projects its organization-scoped role catalog into the identity provider's realm
 * roles — {@code RoleDirectoryPort} and, in this library, {@code KeycloakRoleDirectoryAdapter} over
 * core's {@code KeycloakAdminClient}. That is the <b>only outbound write this module owns</b>:
 * everything else it needs from outside is either read through another feature's named interface or
 * declared as a port for the host application to satisfy.
 *
 * <p>The projection is <b>one way and best-effort</b>. The catalog is the system of record; a
 * {@code RoleDefinition} write publishes {@code RoleDefinitionChangedEvent} /
 * {@code RoleDefinitionDeletedEvent}, and {@code RoleDirectorySyncListener} converges the realm
 * after the transaction commits. Nothing is ever read back from the realm to change a definition,
 * and a failure to project cannot fail the write — {@code RoleDirectoryReconciler} heals the drift on
 * the next start. It exists because a task instance is performed by a <em>role</em>
 * ({@code TaskUse.performedBy}), so "which tasks are mine" is only answerable once the roles a user
 * holds are carried by their token.
 */
@ApplicationModule(
        displayName = "Base Workflow",
        allowedDependencies = {
                "core", "shared", "state :: domain", "state :: operations", "rule :: usecase", "rule :: domain"})
package com.processpuzzle.workflow;

import org.springframework.modulith.ApplicationModule;
