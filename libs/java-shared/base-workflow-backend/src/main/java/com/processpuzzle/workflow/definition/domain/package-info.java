/**
 * JPA entities, repositories, and domain services for the definition side: the tenant-level catalog
 * of role, artifact, task and tool definitions, plus the workflows that use them. Each of the five is
 * a standalone aggregate keyed by {@code (orgKey, id)} — a role or a task belongs to the
 * organization, not to one workflow, and may be composed into any number of them.
 *
 * <p>Not exposed as a Spring Modulith named interface — nothing outside this module needs to reach
 * into definition internals; the execution layer reads definitions through
 * {@link com.processpuzzle.workflow.definition.usecases.inbound.ResolveProcessDefinitionUseCase},
 * which lives in the same module.
 */
package com.processpuzzle.workflow.definition.domain;
