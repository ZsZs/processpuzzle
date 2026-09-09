/**
 * One class per definition-layer operation. Roles, artifacts, tasks and tools are each an
 * independent aggregate with its own five-use-case set ({@code Create}, {@code Replace},
 * {@code Delete}, {@code Find}, {@code FindAll}); a workflow definition references them by id and
 * owns only the wiring that is true of them <em>in that workflow</em>. Mutating a role or a task is
 * therefore an operation on that catalog entry, not on a workflow — the reverse of the arrangement
 * these classes had while the three were children of {@code Workflow}.
 *
 * <p>{@link ResolveWorkflowUseCase} is the seam the execution layer reads through: it
 * pairs each {@code TaskUse} with the shared {@code TaskDefinition} it names and
 * hands back a {@link ResolvedWorkflow}, so nothing downstream has to know that the two halves live
 * in different tables. It refuses a dangling reference rather than resolving around it.
 *
 * <p>The remaining pair, {@link ImportWorkflowsUseCase} and
 * {@link ExportWorkflowUseCase}, moves whole SPEM YAML documents — catalog sections plus
 * workflows — in and out.
 */
package com.processpuzzle.workflow.definition.usecases.inbound;
