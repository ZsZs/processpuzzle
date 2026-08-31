# @processpuzzle/base-workflow

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-workflow-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_workflow_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_workflow_frontend)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-workflow?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-workflow)

## Introduction

`@processpuzzle/base-workflow` is the front-end building block of the ProcessPuzzle Workflow Engine. It provides the Angular constructs to author, monitor, and drive business workflows in a Low-Code application. The library complements the [`base-workflow-backend`](../../java-shared/base-workflow-backend/README.md) Spring Boot module that executes long-running workflows server-side.

## Status

The **definition layer** — authoring a workflow — is implemented: a `Workflow` has a generated list and
form, with its roles, work products and tasks as embedded components, and a task's inputs, outputs and steps
embedded one level deeper. A `Tool Definition`, the external system a step calls, is a routable aggregate of its
own with its operations embedded in it.

The **execution layer** is implemented as far as the contract allows: a `Workflow Instance` has a list and a form
with its task and work product instances, and a task instance's step results below those — all **read-only**.
`base-workflow-api.yaml` defines no `PUT` on the runtime side; an instance is started by `POST /instances`,
cancelled by `DELETE`, and a task moves through `/assign`, `/complete` and `/skip`. Those four verbs have no
front-end surface yet, so the screens monitor a run rather than drive one.

No workflow diagram yet. When one arrives it belongs in an `extraTabs` entry beside List and Details, exactly where
base-state mounts its State Modeler.

## Authoring and monitoring a workflow

The screens are the stock base-entity ones, driven by descriptors this library compiles in. Nothing has to be
authored as metadata first, which is the deliberate difference from a tenant's own entities: a workflow engine is
part of the framework, so its shape changes with a release rather than with a database row.

```ts
// app.config.ts — the facades of the whole graph, and the backend that serves its translations
providers: [
  ...BASE_WORKFLOW_FACADE_PROVIDERS,
  { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { ...BASE_WORKFLOW_ENTITY_FACADES } },
  { provide: TRANSLATION_SOURCE_REGISTRY, useValue: BASE_WORKFLOW_TRANSLATION_SOURCE, multi: true },
];

// app.routes.ts — the three branches, wherever they belong in the application
{ path: 'workflows', children: BASE_WORKFLOW_ROUTES }
```

All thirteen facades or none: a task's `performedBy` resolves through the role facade and a step's `toolId`
through the tool facade, so half a graph is a form that throws on first render.

Copy `src/assets/i18n/base_workflow` to the application's `assets/i18n/base_workflow` (see the testbed's
`project.json`); the scope falls back to the backend's translations resource when the assets are absent.

### The graph

| Entity                     | Role                                        | Identified by |
| -------------------------- | ------------------------------------------- | ------------- |
| `Workflow`       | Aggregate root, `/workflows`                | `id`          |
| `Workflow Role Definition` | Embedded in the workflow                     | `id`          |
| `Work Product Definition`  | Embedded in the workflow                     | `id`          |
| `Task Definition`          | Embedded in the workflow                     | `id`          |
| `Task Input Reference`     | Embedded in a task                          | `refId`       |
| `Task Output Reference`    | Embedded in a task                          | `refId`       |
| `Task Step Definition`     | Embedded in a task                          | `id`          |
| `Tool Definition`          | Aggregate root, `/tools`                    | `id`          |
| `Tool Operation`           | Embedded in the tool                        | `id`          |
| `Workflow Instance`         | Aggregate root, `/instances`, read-only     | `id`          |
| `Task Instance`            | Embedded in the instance, read-only         | `id`          |
| `Work Product Instance`    | Embedded in the instance, read-only         | `id`          |
| `Task Step Result`         | Embedded in a task instance, read-only      | `stepId`      |

Everything below a root travels inside that root's document, so a save is a full replacement of the whole
aggregate — which is also why the list endpoints return the full entity rather than a summary: base-entity's
generated form reads the record out of the loaded list rather than re-fetching it by id.

Every name is prefixed, because `BASE_ENTITY_FACADE_REGISTRY` is one flat map for the whole application: a bare
`Role Definition` is a name a tenant's own metadata could plausibly claim. `Task Input Reference` and
`Task Output Reference` are two names over one model class, because an `EMBEDDED_COMPONENTS` control resolves its
child by name and `inputs` and `outputs` are two lists.

### Read-only screens

base-entity has no read-only-entity flag, so the runtime side assembles it from the two levers it does have:
`isAbstract` on the descriptor, which disables New, Edit and Delete in the toolbar and Save and Delete on the
form, and `disabled` on every attribute, which greys the fields and keeps the form from ever becoming dirty.
Neither *hides* a button — the framework's form template has no `@if` around them — so Save is visible and inert.

### Cross-feature references

base-workflow is a pure orchestrator: it names another feature's resources by id and never duplicates their
models. So `entityRoleId`, `entityTypeId`, `stateMachineId`, `preconditionRuleId`, `postconditionRuleId`,
`entityId` and a task reference's `refId` are all plain text controls rather than pickers — this library holds no
store for base-entity's types, base-rule's rules or base-state's machines, and the backend resolves each on save.
The two references that *are* pickers, `performedBy` and `toolId`, point at entities of this library.

## Configuration

| `BaseConfiguration` key | Meaning                                                                                                        |
| ----------------------- | -------------------------------------------------------------------------------------------------------------- |
| `WORKFLOW_SERVICE_ROOT` | `<host>/organizations/<orgKey>` the workflow endpoints hang off. Optional — falls back to `APP_SERVICE_ROOT`.   |
