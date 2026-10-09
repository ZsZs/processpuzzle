# @processpuzzle/base-event

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-event-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_event_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_event_frontend)

## Introduction

The Angular half of the platform **event catalog**: the list and form of the `Event Definition`s an
organization declares. The backend half, which persists the catalog and publishes the events through the
platform event bus, is [base-event-backend](../../java-shared/base-event-backend/README.md).

An event definition names one kind of fact the platform can observe:

| Field | Meaning |
| --- | --- |
| `id` | Author-chosen and stable, e.g. `OrderCreatedEvent` — what other features store |
| `kind` | `SYSTEM` (raised by the platform), `MESSAGE` or `SIGNAL` (published through the API) |
| `subjectType` | `SYSTEM` only — the entity definition code whose objects raise it, e.g. `order` |
| `action` | `SYSTEM` only — `CREATED`, `UPDATED`, `DELETED` or `STATE_CHANGED` |
| `state` | `STATE_CHANGED` only — the state the subject entered, e.g. `CONFIRMED` |

## Referenced by name, not by import

Other features point at the catalog through the entity name `Event Definition`, never through this
package. base-workflow's start event, for instance, is a `FOREIGN_KEY` with
`linkedEntityType = 'Event Definition'`; the picker resolves the catalog's store through the application's
`BASE_ENTITY_FACADE_REGISTRY`. A host that wants those pickers populated therefore spreads
`BASE_EVENT_ENTITY_FACADES` into the registry.

## Using it

```ts
import { BASE_EVENT_ENTITY_FACADES, BASE_EVENT_FACADE_PROVIDERS, BASE_EVENT_ROUTES, BASE_EVENT_TRANSLATION_SOURCE } from '@processpuzzle/base-event';

providers: [
  ...BASE_EVENT_FACADE_PROVIDERS,
  { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { ...BASE_EVENT_ENTITY_FACADES /* , ...others */ } },
  { provide: TRANSLATION_SOURCE_REGISTRY, useValue: BASE_EVENT_TRANSLATION_SOURCE, multi: true },
];

export const routes: Routes = [{ path: 'events', children: [...BASE_EVENT_ROUTES] }];
```

`BASE_EVENT_ROUTES` mounts the generic list and details screens at `event-definition`
(`snakeCaseName('Event Definition')`) and registers the `base_event` and `base_entity` transloco scopes.
Copy the translations to `assets/i18n/base_event` (see the testbed's `project.json`).

The endpoints are read from `EVENT_SERVICE_ROOT`, falling back to `BACKEND_SERVICE_ROOT`:
`/organizations/{orgKey}/event-definitions`.
