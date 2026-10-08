# base-event-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-event-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_event_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_event_backend)

## Introduction

`base-event-backend` owns the organization's **event catalog** and turns raw platform facts into named events.
It is a Spring Modulith application module (`event`) that depends on `core` and `shared` only.

An `EventDefinition` gives a fact a name the rest of the platform can refer to:

| id | kind | binding |
| --- | --- | --- |
| `OrderCreatedEvent` | SYSTEM | `order`, CREATED |
| `OrderConfirmedEvent` | SYSTEM | `order`, STATE_CHANGED, `CONFIRMED` |

SYSTEM definitions are bound to a fact: `subjectType` (an entity definition code), `action`
(CREATED / UPDATED / DELETED / STATE_CHANGED) and, for STATE_CHANGED only, an optional `state`. MESSAGE and
SIGNAL definitions carry no binding; they can be catalogued now and are thrown by workflows in a later version.

## How an event flows

```
base-entity / base-state ──> PlatformEvent           (shared.event — the raw fact)
                                  │  PlatformEventCatalogListener matches the org's SYSTEM definitions
                                  ▼
                             DefinedEventOccurred    (shared.event — one per matching definition)
                                  │
                                  ▼
                             base-workflow TriggeredStartListener — starts workflows whose
                             TRIGGERING_EVENT start event names the definition
```

Both events live in `api-contracts`' `com.processpuzzle.shared.event`, so no feature compiles against another.
Both hops are `@TransactionalEventListener` + `REQUIRES_NEW`, and the host application's Spring Modulith
event publication registry (`spring-modulith-events-jpa`) makes them durable: a publication is retried until
its listener completes. Listeners must therefore be idempotent.

## API

`/organizations/{orgKey}/event-definitions` — CRUD plus `/import` (YAML), see
[`base-event-api.yaml`](../api-contracts/src/main/resources/base-event-api.yaml). The list returns full
definitions; PUT takes an optional `version` for optimistic locking.

## Seeding

With `base-event.loadDefaultEventDefinitions=true` the module imports
`classpath*:default-event-definitions/base-event/<orgKey>-event-definitions.yaml` at startup. The import
**upserts**, so editing a seed file reaches a stack that has already been seeded.

## Host application

The host answers base-workflow's `EventCatalogPort` from `FindEventDefinition` (the `event :: usecase` named
interface) — see `EventCatalogAdapter` in the testbed backend.
