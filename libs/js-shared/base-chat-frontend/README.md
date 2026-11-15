# @processpuzzle/base-chat

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-chat-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_chat_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_chat_frontend)

## Introduction

The Angular half of the platform **chat**: conversations bound to a platform subject — an entity instance, a
workflow instance — with live delivery and unread counts. The backend half is
[base-chat-backend](../../java-shared/base-chat-backend/README.md); the design is in
[docs/chat/base-chat-design-plan.md](../../../docs/chat/base-chat-design-plan.md).

**Status: scaffold.** The library registers the `base_chat` translation scope and nothing else yet; the
conversation store, the stream service and the panel widgets arrive in phase 4 of the plan.

## Layers

Two layers under `src/lib`, as in `base-event` and `base-calculation`:

| Layer | Holds | May import |
| --- | --- | --- |
| `domain/` | Types, REST and stream services, signal stores | other `domain/` files only |
| `feature/` | Panel, topics list, unread badge, definition authoring screens | `domain/` |

The routes and providers files sit above both. `eslint.config.mjs` enforces the direction with
`no-restricted-imports`: a `domain/` file that imports from `feature/`, the routes or the providers fails
`nx lint`.

## Translations

Scope `base_chat`, with the alias spelled out wherever it is registered — transloco would otherwise
camel-case it to `baseChat`. A host copies `src/assets/i18n/base_chat` to `assets/i18n/base_chat`.
