/**
 * JPA entities and repositories for the runtime side: workflow/task/artifact instances. Unlike
 * {@code definition.domain}, this is exposed as the module's {@code domain} named interface —
 * other modules that eventually need read-only visibility into "is this task done", "what state
 * is this artifact in" (a BFF, a dashboard module, base-app) can depend on
 * {@code workflow :: domain} without reaching into use cases or ports.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.processpuzzle.workflow.execution.domain;
