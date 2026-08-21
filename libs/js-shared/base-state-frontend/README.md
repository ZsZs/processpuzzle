# @processpuzzle/base-state

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-state-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_state_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_state_frontend)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-state?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-state)

## Introduction

`@processpuzzle/base-state` is the front-end building block of the ProcessPuzzle State Machine. It provides the
Angular constructs needed to model, drive and visualize state-driven flows in a Low-Code application. The library
complements the [`base-state-backend`](../../java-shared/base-state-backend/README.md) Spring Boot module that
persists state machines and orchestrates transitions server-side.

## Status

The **knowledge layer** — authoring a state machine — is implemented: a `State Machine Definition` has a generated
list and form, with its states and transitions as embedded components, and a transition's guards and actions
embedded one level deeper. The **operation layer** of `base-state-api.yaml` — an entity object's current state and
`fireStateTransition` — has no frontend yet; nor has the state machine diagram, which will arrive as an extra tab
on the definition's screens.

## Authoring a state machine

The screens are the stock base-entity ones, driven by descriptors this library compiles in. Nothing has to be
authored as metadata first, which is the deliberate difference from a tenant's own entities: a state machine is
part of the framework, so its shape changes with a release rather than with a database row.

```ts
// app.config.ts — the facades of the whole graph, and the backend that serves its translations
providers: [
  ...BASE_STATE_FACADE_PROVIDERS,
  { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { ...BASE_STATE_ENTITY_FACADES } },
  { provide: TRANSLATION_SOURCE_REGISTRY, useValue: BASE_STATE_TRANSLATION_SOURCE, multi: true },
];

// app.routes.ts — the authoring branch, wherever it belongs in the application
{ path: 'state-machines', children: BASE_STATE_ROUTES }
```

Copy `src/assets/i18n/base_state` to the application's `assets/i18n/base_state` (see the testbed's
`project.json`); the scope falls back to the backend's translations resource when the assets are absent.

### The graph

| Entity                     | Role                                         | Identified by |
| -------------------------- | -------------------------------------------- | ------------- |
| `State Machine Definition` | Aggregate root, one per governed entity type | `entityName`  |
| `State Machine State`      | Embedded in the definition                   | `key`         |
| `State Machine Transition` | Embedded in the definition                   | `key`         |
| `State Transition Guard`   | Embedded in a transition                     | `beanName`    |
| `State Transition Action`  | Embedded in a transition                     | `beanName`    |

Everything below the root travels inside the definition's document — the contract gives none of them an endpoint
of its own — so a save is a full replacement of the whole machine. A definition is addressed by the entity type it
governs rather than by a key of its own, and `StateMachineDefinition.id` is a mirror of `entityName` that lets the
generic screens address it unchanged.

## Configuration

| `BaseConfiguration` key | Meaning                                                                                                            |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `STATE_SERVICE_ROOT`    | `<host>/organizations/<orgKey>` the state machine endpoints hang off. Optional — falls back to `APP_SERVICE_ROOT`. |
