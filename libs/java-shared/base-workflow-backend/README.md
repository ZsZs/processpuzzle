# ProcessPuzzle :: Base Workflow Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-workflow-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_workflow_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_workflow_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-workflow-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-workflow-backend)

ProcessPuzzle Base Workflow Backend is the server-side companion of [`@processpuzzle/base-workflow`](../../js-shared/base-workflow-frontend/README.md). It provides the building blocks for defining, executing, and monitoring long-running business workflows in a Spring Boot application. It builds on top of [`base-state-backend`](../base-state-backend/README.md).

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Architecture

Hexagonal, following ProcessPuzzle's convention (`adapters/inbound`, `adapters/outbound`, `usecases/inbound`, `usecases/outbound`, `domain`), split into two layers under `com.processpuzzle.workflow`:

- **`definition`** — design-time authoring, split the way SPEM splits Method Content from Process (see `api-contracts/src/main/resources/SPEM-Concept.md`). `RoleDefinition`, `ArtifactDefinition`, `TaskDefinition` and `ToolDefinition` are a tenant-level *catalog*: each is a standalone aggregate keyed by `(orgKey, id)`, authored once through its own CRUD resource, and composed into any number of workflows. They reference each other by plain id — a role is `responsibleFor` artifact ids, a task names role ids and artifact ids — which is what lets the authoring UI render each reference as a `RELATED_ENTITIES` control over the referenced entity's own list.

  A `Workflow` *uses* them: its `roles`, `artifacts`, `tools` and `tasks` hold `RoleUse` / `ArtifactUse` / `ToolUse` / `TaskUse` objects, each naming a definition by id plus whatever is true of it only in that workflow. `TaskUse` is the one with content today — which of a task's permitted roles performs it here, what it waits for (`dependsOn`), how that wait is satisfied (`joinType`) and whether it may run alongside its siblings (`parallel`). The other three are a definition id and nothing else, deliberately: a use is an object rather than a bare string so that per-workflow configuration can be added later without changing the shape of the reference list. There is no `WorkflowUse` and no `WorkflowDefinition` — a workflow is not reused inside another one, it is `extends`-ed, which is a different relation.

  `Workflow.startCondition` says how an instance comes into being (an input artifact reaching a state, a triggering event, a manual launch by an authorized role, or a time-based precondition). It is one flat value with a `startType` discriminant rather than a subtype per mechanism, which is how every ProcessPuzzle contract models a variant and what keeps it a plain Jackson round-trip in its JSONB column.

  `WorkflowValidator` enforces referential integrity when a workflow is *saved* — every use resolves, every `performedBy` is a role both the workflow and the task allow, every artifact a task touches is declared. The catalog delete guards (`DeleteRoleDefinitionUseCase` and siblings, over `CatalogReferenceScanner`) are the other half of the same invariant. `ResolveProcessDefinitionUseCase` pairs the two halves back up for the execution layer, refusing a dangling reference rather than resolving around it.
- **`execution`** — runtime interpretation of a workflow: `ProcessInstance`, `TaskInstance`, `ArtifactInstance` are three *independent* aggregates (not one nested tree) so that completing unrelated parallel tasks never contends on a shared optimistic lock. `TaskActivationService` is the engine: it decides which PENDING/BLOCKED tasks become ACTIVE after every state change, honoring `dependsOn`, `joinType` and the `parallel` flag. An instance is a snapshot: it keeps the task ids and artifact names it was born with, so a later edit to a shared catalog entry does not rewrite a process already in flight.

Per the API contract, base-workflow is a pure orchestrator: rule evaluation is delegated to base-rule (`rule :: usecase`), state machine transitions to base-state (`basestate :: domain`) — the only two feature modules this one is allowed to depend on, and only through their published named interfaces. It never depends on base-entity or base-artifact directly (neither exposes one yet); instead it defines outbound ports (`RoleMembershipPort`) that the host application implements, the same pattern base-app-backend uses for `OrganizationAccessPolicy`.

## Known gaps

- **No `base-workflow-events.yaml` contract exists yet.** The events in `execution.events` are this module's own best-effort design of what other modules would plausibly need to hear about (process/task lifecycle, artifact creation), not a negotiated shared schema. Revisit their shape once one exists.
- **No inbound listener from base-state.** `ArtifactInstance.currentState` is never refreshed after creation — base-state doesn't yet expose an instance-level event to listen for (it's still a scaffold). `ArtifactInstanceCreatedEvent` is the first half of that integration; the second half (a listener that updates `currentState`) doesn't exist yet.
- **The close-out check can miss.** `CompleteTaskUseCase` marks the instance COMPLETED when `allTerminal` says every task is terminal. Two concurrent completions of the last two tasks each read the other as still running under READ_COMMITTED, so neither closes the instance and it stays ACTIVE with nothing left to do. Distinct from the context contention fixed by `ProcessContext` — that was a lost update, this is a missed transition — and it needs either a serialized close-out or a listener on `TaskCompletedEvent` that re-checks after commit.
- **Tool step input/output mapping is a placeholder.** `StepDefinition.inputMapping`/`outputMapping` are documented as PPCL expressions / JSONPath, but base-workflow doesn't own an expression engine. `ToolStepExecutor` currently does direct top-level key lookups only.
- **Old columns and tables linger after the Definition/Use split.** Roles, artifacts and tasks used to be JPA children of the workflow row and are now their own tables; `ArtifactDefinition.type`/`entityTypeId` are now `artifactType`/`artifactTypeId`; a workflow's reference lists changed from id arrays to `...Use` objects. With `ddl-auto: update` none of that is dropped or migrated — an existing database keeps its rows and needs the seed file re-imported, or the columns moved by hand. The table name is still `workflow_process_definition` for the same reason: renaming it would orphan the existing table rather than migrate it.
- **Only this module's PUTs honour a client-supplied version.** The five `*Input` schemas carry an optional `version`; supply the one you read and a stale write is refused with 409 instead of silently overwriting a concurrent edit. base-rule, base-state and base-document keep `version` on the read schema only and rely on Hibernate's `@Version` detected at flush — which catches two genuinely overlapping transactions but not the ordinary case of a form loaded minutes ago, because each request loads the row fresh inside its own transaction and so has nothing stale to detect. Worth lifting to the other modules; the shape to copy is `WorkflowDefinitionMapper`'s `version` pass-through plus the existing guard in each replace use case.
- **The frontend has not been migrated.** `base-workflow-frontend`'s hand-written mappers and descriptors still mirror the pre-split contract (typed `TaskIOReference` inputs, bare-id reference lists, no start condition), so the authoring UI will not round-trip against these endpoints until it is updated.
- **The REST resource is still called a process.** Paths, `operationId`s and OpenAPI tags say `processes`/`processId` — and so, following the generated API interfaces, do the endpoint classes — while the schemas and domain classes say `Workflow`. Deliberate, to keep the frontend working through the rename; worth finishing in one commit alongside the frontend migration.

## Development

```powershell
npm exec nx build base-workflow-backend
npm exec nx test base-workflow-backend
npm exec nx lint base-workflow-backend
```

## License

This project is licensed under the Apache License 2.0.
