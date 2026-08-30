/**
 * Base Workflow: SPEM-inspired orchestrator that interprets process definitions (roles,
 * artifacts, tasks, tools) to execute and monitor long-running process instances.
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
 */
@ApplicationModule(
        displayName = "Base Workflow",
        allowedDependencies = {
                "core", "shared", "state :: domain", "state :: operations", "rule :: usecase", "rule :: domain"})
package com.processpuzzle.workflow;

import org.springframework.modulith.ApplicationModule;
