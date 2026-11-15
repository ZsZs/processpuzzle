# @processpuzzle/base-chat

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-chat-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_chat_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_chat_frontend)

## Introduction

The Angular half of the platform **chat**: conversations bound to a platform subject — an entity instance, a
workflow instance — with live delivery and unread counts. The backend half is
[base-chat-backend](../../java-shared/base-chat-backend/README.md); the design is in
[docs/chat/base-chat-design-plan.md](../../../docs/chat/base-chat-design-plan.md).

**Status: panel, badge and live stream (phase 4).** The library gives an application a conversation on a record —
history, a message box, unread counts, live delivery — as two widgets and the services behind them. Authoring chat
definitions in the designer and the topics list are later phases.

## Using it

```typescript
// app.config.ts
providers: [
  ...provideChatWidgets(),              // registers chat-panel and chat-unread-badge with the widget registry
  provideChatWorkingContextSubject(),   // optional: a panel without a subjectId follows the active working context
  { provide: CHAT_IDENTITY, useFactory: createChatIdentity }, // what the library needs from the application
]
```

`CHAT_IDENTITY` is the one thing the application must supply: `accessToken()` (the live stream is read with
`fetch`, which the HTTP interceptors do not see, so the library cannot borrow the token from them) and `userId()`
(the id messages carry as their author, to tell the user's own from the others'). The library names no identity
provider. The service root is `CHAT_SERVICE_ROOT` from the runtime configuration, falling back to
`BACKEND_SERVICE_ROOT`; the application copies `src/assets/i18n/base_chat` to `assets/i18n/base_chat`.

| Widget | Inputs | Shows |
| --- | --- | --- |
| `chat-panel` (`pp-chat-panel`) | `subjectId` (else the active working context), `definitionKey` | the conversation of one record with a message box; a "Start a chat" button when it has none and `definitionKey` is set |
| `chat-unread-badge` (`pp-chat-unread-badge`) | `max` (99) | how many messages are unread across everything the user follows; nothing at zero |

## How it works

- **`ChatStore`** holds the conversations seen and the messages loaded of some of them, shared by every panel and
  badge. Sending is optimistic: a message shows at once as pending, the server's answer or the same message on the
  stream replaces it by its client-generated id, and a failed one stays with a retry that reuses the id — so it can
  never post twice. Unread is derived (`lastSeq − lastReadSeq`), not counted. A conversation on screen is marked read
  as it is shown and for every message that arrives meanwhile.
- **`ChatStreamService`** reads the server-sent event stream with `fetch` and `SseParser`: one stream for
  everything the user follows, reference-counted by the store. It reconnects at once when the server ends a healthy
  stream (it does, at the token's expiry), with backoff after a failure, and drops a connection silent for 70 s. Each
  connect says per conversation what the client already holds, so what it missed is replayed.
- Message text is rendered as text, never as markup.

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
