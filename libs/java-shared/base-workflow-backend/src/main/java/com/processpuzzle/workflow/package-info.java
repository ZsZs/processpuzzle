/**
 * Base Workflow: SPEM-inspired orchestrator that interprets process definitions (roles, work
 * products, tasks, tools) to execute and monitor long-running process instances.
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
 *   <li>{@code state :: domain} — read-only visibility into state/transition definitions.
 *       Base State does not yet expose an instance-level API (it is still a scaffold), so
 *       WorkProductInstance.currentState is maintained locally and refreshed by a state-change
 *       event listener once Base State publishes one; see
 *       {@code execution.usecases.outbound.WorkflowEventPublisherPort} for the outbound half of
 *       that contract.
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
 */
@ApplicationModule(
        displayName = "Base Workflow",
        allowedDependencies = {"core", "shared", "state :: domain", "rule :: usecase", "rule :: domain"})
package com.processpuzzle.workflow;

import org.springframework.modulith.ApplicationModule;
