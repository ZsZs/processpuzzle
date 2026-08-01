/**
 * Base Workflow: interprets workflow (process) definitions to execute and monitor long-running
 * processes.
 *
 * <p>Still a scaffold, but the dependency on Base State is real — it is already declared in
 * {@code pom.xml}, and a workflow step advances on a state change. {@code shared} is needed because
 * the generated {@code workflow.api} returns the shared {@code ImportResult}.
 */
@ApplicationModule(
        displayName = "Base Workflow",
        allowedDependencies = {"basestate :: domain", "shared"})
package com.processpuzzle.workflow;

import org.springframework.modulith.ApplicationModule;
