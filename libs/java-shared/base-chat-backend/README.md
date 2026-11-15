# base-chat-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-chat-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_chat_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_chat_backend)

## Introduction

`base-chat-backend` holds **conversations bound to platform subjects** — an entity instance, a workflow
instance — with role-based access, history, read markers and live delivery over SSE. It is a Spring Modulith
application module (`chat`) that depends on `core` and `shared` only; what it needs from the rest of the
platform it declares as outbound ports. The Angular half is
[base-chat-frontend](../../js-shared/base-chat-frontend/README.md).

**Status: core API and live delivery (phases 2 and 3).** Chat definitions, conversations, messages, read markers,
unread counts and a live stream exist behind the REST API of `base-chat-api.yaml`; the Angular half is phase 4.
The design decisions, and where the build differs from the plan, are recorded in
[docs/chat/base-chat-design-plan.md](../../../docs/chat/base-chat-design-plan.md).

## What it does

| Operation | Path under `/organizations/{orgKey}/chat` | Notes |
| --- | --- | --- |
| List / get / save a definition | `definitions`, `definitions/{key}` | `revision` draft or released; saving needs the design right and announces the roles it names |
| Find conversations of a subject | `conversations?subjectType&subjectId` | only the ones the caller may read |
| Open a conversation | `POST conversations` | 404 not yours to know · 403 no create role · 409 exists / not openable by hand |
| Get a conversation | `conversations/{id}` | with `permissions.canWrite` |
| Page history | `conversations/{id}/messages?before&limit` | newest first, by `seq` cursor |
| Post a message | `POST conversations/{id}/messages` | idempotent on `clientMessageId`: 201 new, 200 replay |
| Mark read / unfollow | `PUT conversations/{id}/read`, `DELETE conversations/{id}/follow` | the read marker is also the follow record; it only moves forward |
| Followed conversations | `GET unread` | with `unread` = `lastSeq - lastReadSeq`, only what the caller may still read |
| Live stream | `GET stream?after=<id>:<seq>,…` | server-sent events, one connection per user and tab for everything followed |

Access is computed on every call (`ResolveAccess`) from the released definition's role lists, the caller's
realm roles and the subject's state — nothing about participants is stored. The subject comes through
`SubjectResolverPort`, which the host implements (`ChatSubjectAdapter` in the testbed, over base-entity and
base-state); without an adapter every subject exists. Message numbers (`seq`) are gap-free per conversation:
see `ConversationRepository#lockIfStatus`.

**Live delivery.** `OpenChatStream` snapshots, on the caller's thread, the followed conversations they may read;
`ChatStreamRegistry` then matches `MessagePostedEvent` and `ReadMarkerChangedEvent` — observed after commit — to
the connections that carry them. A connection ends at its token's expiry (at most
`base-chat.stream.max-age-minutes`, default 15) and the client reconnects, which re-authorizes; a heartbeat
comment every `base-chat.stream.heartbeat-seconds` (25) keeps proxies from closing it. Delivery is in-process and
best effort: a client that missed something replays from the `seq` it holds (`after`).

Seed files `default-chat-definitions/base-chat/<orgKey>-chat-definitions.yaml` are imported and released at
startup when `base-chat.loadDefaultChatDefinitions` is on.

## Layout

```
com.processpuzzle.chat
  definition/       ChatDefinition authoring and release   ┐ each part has the same layers:
  conversation/     conversations, messages, read markers  ┘ domain · usecases/{inbound,outbound} · adapters/{inbound,outbound} · events
  common/           properties, error mapping
```

Same shape as `base-workflow-backend`: repositories sit in `domain`, an outbound port is an interface in
`usecases/outbound` (with a `PermitAll…` default where a missing answer must not mean "no"), and the host
application supplies the adapters.

## Verifying the layers

Two checks, at different levels:

| Check | Verifies | Runs |
| --- | --- | --- |
| `ChatModularityTests` | Edges **between** modules — `chat` may use `core` and `shared` only | `mvn test` |
| `HexagonalLayerRules` via `arch-unit-maven-plugin` | The direction of the layers **inside** the module | `mvn process-test-classes`, so every build |

The rules (static `ArchRule` fields in `src/test/java/.../architecture/HexagonalLayerRules.java`):

1. `domain` depends on no use case, no adapter and no HTTP or generated-DTO type.
2. `usecases` depend on no adapter and no HTTP or generated-DTO type.
3. Inbound adapters do not reach outbound adapters.
4. `usecases.outbound` holds interfaces — plus the records and enums that are the data of their contract, and the `PermitAll…` defaults.
5. `definition` does not depend on `conversation`.
6. `conversation` does not depend on `definition.adapters`.

The plugin finds the rule fields by reflection in this module's own test classes, which is why its execution is
bound to `process-test-classes` — its default phase runs before they are compiled. It checks main classes only.
`HexagonalLayerRulesTest` runs each rule against deliberately violating fixture classes (in test sources), so a
rule that silently stops matching fails a test instead of passing for ever.

Every rule allows an empty match, so a layer without classes is not a violation.
