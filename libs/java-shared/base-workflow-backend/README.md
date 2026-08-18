# ProcessPuzzle :: Base Workflow Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-workflow-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_workflow_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_workflow_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-workflow-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-workflow-backend)

ProcessPuzzle Base Workflow Backend is the server-side companion of [`@processpuzzle/base-workflow-frontend`](../../js-shared/base-workflow-frontend/README.md). It provides the building blocks for defining, executing, and monitoring long-running business workflows in a Spring Boot application. It builds on top of [`base-state-backend`](../base-state-backend/README.md).

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Architecture

Hexagonal, following ProcessPuzzle's convention (`adapters/inbound`, `adapters/outbound`, `usecases/inbound`, `usecases/outbound`, `domain`), split into two layers under `com.processpuzzle.workflow`:

- **`definition`** — design-time authoring of process definitions (roles, work products, tasks) and tool definitions. `ProcessDefinition` is the sole aggregate root; roles/work products/tasks are mutated through it even via their own sub-resource endpoints, so `ProcessDefinition.version` stays a meaningful optimistic-lock guard over the whole definition.
- **`execution`** — runtime interpretation of a process definition: `ProcessInstance`, `TaskInstance`, `WorkProductInstance` are three *independent* aggregates (not one nested tree) so that completing unrelated parallel tasks never contends on a shared optimistic lock. `TaskActivationService` is the engine: it decides which PENDING/BLOCKED tasks become ACTIVE after every state change, honoring `dependsOn` and the `parallel` flag.

Per the API contract, base-workflow is a pure orchestrator: rule evaluation is delegated to base-rule (`rule :: usecase`), state machine transitions to base-state (`basestate :: domain`) — the only two feature modules this one is allowed to depend on, and only through their published named interfaces. It never depends on base-entity or base-artifact directly (neither exposes one yet); instead it defines outbound ports (`RoleMembershipPort`) that the host application implements, the same pattern base-app-backend uses for `OrganizationAccessPolicy`.

## Known gaps

- **No `base-workflow-events.yaml` contract exists yet.** The events in `execution.events` are this module's own best-effort design of what other modules would plausibly need to hear about (process/task lifecycle, work product creation), not a negotiated shared schema. Revisit their shape once one exists.
- **No inbound listener from base-state.** `WorkProductInstance.currentState` is never refreshed after creation — base-state doesn't yet expose an instance-level event to listen for (it's still a scaffold). `WorkProductInstanceCreatedEvent` is the first half of that integration; the second half (a listener that updates `currentState`) doesn't exist yet.
- **Tool step input/output mapping is a placeholder.** `StepDefinition.inputMapping`/`outputMapping` are documented as PPCL expressions / JSONPath, but base-workflow doesn't own an expression engine. `ToolStepExecutor` currently does direct top-level key lookups only.
- **RSQL filtering on the `roles`/`tasks` sub-resource list endpoints isn't applied.** Both collections live inside the already-loaded `ProcessDefinition` aggregate rather than a queryable table (see `RoleDefinitionsEndpoint`), so `RsqlSpecificationBuilder` — which builds JPA `Specification`s against a query root — doesn't have anywhere to attach.
- **This module's endpoint/mapper code was written without being able to run `mvn generate-sources`** (no Maven/network access to Maven Central in the environment it was built in). Two specific things are flagged inline and worth a five-minute check on first build:
  - `ProcessDefinitionInput.extends` — a Java reserved word — assumed to keep semantic `getExtends()`/`setExtends()` accessor names (see `WorkflowDefinitionMapper`'s Javadoc).
  - `cancelProcessInstance` / `skipTask`'s untitled inline `{reason: string}` request bodies — assumed to generate as a raw `Object` parameter (see `ProcessInstancesEndpoint`'s Javadoc).

## Development

```powershell
npm exec nx build base-workflow-backend
npm exec nx test base-workflow-backend
npm exec nx lint base-workflow-backend
```

## License

This project is licensed under the Apache License 2.0.
