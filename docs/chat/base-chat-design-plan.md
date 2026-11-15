# base-chat – Review of the Blueprint and Design Plan

2026-11-15 · reviews [ProcessPuzzle-Chat-Architecture-Blueprint.md](ProcessPuzzle-Chat-Architecture-Blueprint.md)

The blueprint's core shape is sound: a conversation core that knows nothing about subjects, Keycloak or media;
participation computed from roles at access time; plain relational tables for messages; REST to post and SSE
to deliver; calls deferred behind a port. This document records where the blueprint clashes with how the
platform is actually built, and turns the result into an implementation plan.

## Part 1 – Review

### A. Will break unless changed

| # | Blueprint says | Problem | Change |
| --- | --- | --- | --- |
| A1 | SSE stream, `Last-Event-ID` resume | The browser `EventSource` cannot send an `Authorization` header, and the platform authenticates with Keycloak bearer tokens through `auth-token.interceptor`. The base-ai pattern (`HttpClient`, `observe: 'events'`, `partialText`) keeps the **whole** response body in memory, which is fine for one assistant turn but grows without limit on a stream that stays open for hours. | A small `fetch` + `ReadableStream` SSE reader that sets the bearer header itself and sends `Last-Event-ID` when it reconnects. No ticket in the query string (it would end up in proxy logs). |
| A2 | "SSE streams re-check authorization when a token refreshes" | The server never sees a refreshed token on a stream that is already open. | The server closes each stream at the token's `exp` (and at least every 15 min). The client reconnects with a fresh token and resumes. Authorization is checked again on reconnect, which gives the blueprint's guarantee. |
| A3 | "Works behind a standard reverse proxy" | `nginx.conf` `location /api/` keeps nginx's defaults: `proxy_buffering on` and a 60 s `proxy_read_timeout`. SSE frames would be held back, and idle streams cut off after 60 s. | Backend sends `X-Accel-Buffering: no` on stream responses, plus a heartbeat comment every 25 s. No nginx change is needed. (base-ai's delta streaming probably has the same buffering issue today; worth checking.) |
| A4 | `id (monotonic)` used as cursor and `Last-Event-ID` | A `bigserial` is not commit-ordered. Transaction T1 can take id 10, T2 take 11 and commit first. A client that has seen 11 then resumes "after 11" and **never** receives 10. | A per-conversation sequence number `seq`, assigned with `UPDATE chat_conversation SET last_seq = last_seq + 1 … RETURNING last_seq` inside the posting transaction. That row lock serializes posts per conversation, so the order is gap-free and matches commit order. Cursors and unread counts use `(conversation_id, seq)`. On the multiplexed stream the event id is a vector or a per-user cursor, see C3. |
| A5 | Subject adapters "in base-entity, base-workflow and the rule module" | Breaks the rule that feature libraries have no compile dependency on each other: the adapter would pull base-chat's port into base-entity. | `SubjectResolverPort` lives in base-chat. Its adapters go in the composition root (`apps/processpuzzle-testbed-backend/.../composition`). When a port cannot answer, it permits. |
| A6 | Consumes `WorkflowInstanceCompleted`, entity state changes | `WorkflowInstanceCompletedEvent` is in base-workflow's own package (`execution/events`), so listening to it is a compile edge. Entity and state changes already arrive as `shared.event.PlatformEvent` (`subjectType`, `subjectId`, `STATE_CHANGED`, `state`). | Listen only to `PlatformEvent`. For STATE_BOUND chats on workflow instances, base-workflow first has to publish a `PlatformEvent` when an instance completes (small prerequisite). |
| A7 | Flyway-managed tables | The platform runs on `ddl-auto: update` and has no Flyway yet. | JPA entities like every other lib. Introducing Flyway is a platform-wide decision, not a chat task. |
| A8 | `tenantId`, paths `/chat/...` | The platform scopes by `orgKey`, carried in the path `/organizations/{orgKey}/...` and checked against `CurrentPrincipal` (see `OrganizationGuard`). | Rename to `org_key`. Prefix every path with `/organizations/{orgKey}/chat`. |

### B. Clashes with platform conventions

- **B1 Durable events per message.** In the platform `MessagePosted` would go through the Modulith JPA
  publication registry: one outbox row per message per listener, on a stage host that is short of RAM and
  where a broker was deliberately rejected. *Change:* fan out to SSE from a plain in-memory after-commit
  listener (not durable; clients recover from history and resume). Durable events only for
  `ConversationOpened` and `ConversationClosed`, published as `PlatformEvent(subjectType="Conversation")` so
  the base-event catalog can start workflows from them. Per-message platform events stay opt-in per
  definition and come later.
- **B2 Generated Angular client.** `api-contracts` generates server DTOs only; every frontend service is
  hand-written. Don't introduce TypeScript generation just for chat. Keep `base-chat-api.yaml` as the contract
  and hand-write `ChatService`.
- **B3 Frontend libraries.** `chat-data-access` / `chat-ui` / `chat-designer` breaks the one-lib-per-feature
  pattern (`libs/js-shared/base-chat-frontend`, published as `@processpuzzle/base-chat`, with `domain/` and
  `ui/` folders). The designer application is in the private biz repo, so the definition editor ships in
  `base-chat-frontend`, as base-workflow's and base-rule's editors do.
- **B4 Backend layout.** `application/`, `port/in`, `port/out` is not the house style. Follow base-workflow:
  `chat/definition/…` and `chat/conversation/…`, each with `domain`, `usecases/inbound|outbound`,
  `adapters/inbound|outbound`, `events`.
- **B5 Definitions lifecycle is under-specified.** "Versioned with other definitions" means concretely:
  - `DRAFT`/`RELEASED` channels and a `ReleaseParticipant` (new `DefinitionKinds` rung, after workflow).
  - `DefinitionReference`s to the subject's entity or workflow definition.
  - A `DefinitionImportParticipant` for starters.
  - Publishing `RolesReferenced` for `readRoles`, `writeRoles` and `createRoles`, so that org-admin creates
    the missing roles and the role pickers can offer the role catalog.
- **B6 Multi-instance.** The platform runs one backend per stack and has decided against a broker. Keep the
  in-memory broadcaster; when a second instance is needed, use Postgres `LISTEN/NOTIFY`, which adds no new
  service. Drop RabbitMQ from the options.

### C. Design gaps and simplifications

- **C1 TOPIC scope via route pattern.** Storing `/races/:raceId/protests` and `routeParams` couples the
  backend to frontend routes, which change in AppDefinition and in the working-context route shape
  `/ctx/:contextName/:id/...`. The example *is* a subject chat: subject = Race, many conversations, each with
  a title. *Proposal:* drop `scope`/`TopicRoute` and add `multiplicity: SINGLE | MANY` (MANY = user-titled
  topics) to the subject binding. The panel finds its subject from inputs or from the working context. There are no
  subject-less chats in v1 (D1).
- **C2 Unread across "all readable conversations".** Participation is computed from roles, so no user has a
  conversation list. `GET /chat/unread` would have to enumerate every conversation the user's roles can read
  (a dispatcher would see unread counts on every order). *Proposal:* a user **follows** a conversation once
  they open or post in it (the ReadMarker row is the follow record). Unread counts and the multiplexed stream
  cover followed conversations plus the ones open on screen; unfollow deletes the marker.
- **C3 Per-conversation vs multiplexed stream.** Phase 2 already needs unread badges, and HTTP/1.1 allows six
  connections per origin, so a few panels or tabs would use them all up. *Decide now:* **one multiplexed
  stream per user**, filtered to followed and currently viewed conversations. The client changes its set with
  `PUT …/stream/subscriptions`. Resume works by sending the last `seq` per conversation, not one global
  `Last-Event-ID`.
- **C4 Subject visibility.** base-entity has no read roles per object or per definition (only documents have
  `readerRoles`). Today "the caller may see the subject" amounts to "the subject exists in this
  organization". That is fine for v1, but the port should say so instead of promising more. `createdBy` exists
  on `EntityObject` (`Auditable`), so `includeSubjectOwner` is feasible for entities.
- **C5 Author identity.** Without the Keycloak admin API, names can't be looked up later. Store `author_sub`
  and an `author_display_name` snapshot from the token (`CurrentPrincipal`). GDPR erasure anonymizes the
  snapshot and the body.
- **C6 Idempotency.** Keep the client-generated id as a separate `client_message_id` (UUID), unique per
  `(conversation_id, author_sub)`. It must not double as the server id.
- **C7 Error rule.** 403 and 404 overlap. Rule: **404** when the caller cannot read the conversation or the
  subject; **403** only when they can read but not write or create; **409** when the conversation is closed
  or a SINGLE binding already exists.
- **C8 Edit and delete.** The blueprint lists SSE events for edit and delete but no endpoints. Either add
  `PATCH`/`DELETE …/messages/{seq}` (own messages, within a time window) or drop the events from v1.
  Proposal: drop them from v1 and keep soft delete for moderation in phase 6.
- **C9 STATE_BOUND** needs a field: `writableStates: [..]` (and optional `archiveStates`), referencing the
  subject's state machine, checked at release time.
- **C10 "Rule" as subject kind.** Chat about a rule is a designer concern. The generic `subjectType` covers it,
  so drop it from the v1 list and ship entity and workflow-instance adapters only.
- **C11 Definition authoring.** Like `BaseRule`, a `ChatDefinition` could be authored through a metadata
  entity screen first, with a dedicated editor only if the generic form falls short. This would make phase 4
  much smaller.

## Part 2 – Design plan

### Revised model

**ChatDefinition** (org-scoped, `DRAFT`/`RELEASED`):

```yaml
key: order-discussion
subject: { type: ENTITY, definition: Order }   # definition = entity `name`, as in base-rule contexts
multiplicity: SINGLE                           # SINGLE | MANY (titled topics)
lifecycle: ON_DEMAND                           # AUTO_CREATE | ON_DEMAND | STATE_BOUND
writableStates: []                             # STATE_BOUND only
policy: { readRoles: [..], writeRoles: [..], createRoles: [..], includeSubjectOwner: false }
retention: { days: null }
```

**Tables:**

| Table | Columns | Constraints and notes |
| --- | --- | --- |
| `chat_definition` | key, org_key, channel, content (JSON) | — |
| `chat_conversation` | id, org_key, definition_key, subject_type, subject_id, title, status, last_seq, created_by, created_at | unique (org, definition, subject) when SINGLE |
| `chat_message` | conversation_id, seq, client_message_id, author_sub, author_display_name, kind, body, created_at, deleted_at | PK (conversation_id, seq); unique (conversation_id, author_sub, client_message_id) |
| `chat_read_marker` | conversation_id, user_sub, last_read_seq | Is also the follow record |

### Backend — `libs/java-shared/base-chat-backend`, module `chat`

Root package `com.processpuzzle.chat` with `package-info.java`:
`@ApplicationModule(displayName = "Base Chat", allowedDependencies = {"core", "shared"})`. The layout mirrors
base-workflow: one sub-package per bounded part, each split into the same layers.

```
com.processpuzzle.chat
  definition/
    domain/                 ChatDefinition, SubjectBinding, ParticipantPolicy, Lifecycle, ChatDefinitionRepository
    usecases/inbound/       SaveChatDefinition, FindChatDefinition, ChatDefinitionReleaseParticipant,
                            ChatDefinitionImportParticipant
    usecases/outbound/      ChatDefinitionReferencePort (subject definition exists, state names valid; permits by default)
    adapters/inbound/       ChatDefinitionEndpoint, ChatDefinitionMapper
    events/                 (RolesReferenced is published from core, no own class)
  conversation/
    domain/                 Conversation, Message, ReadMarker, ConversationStatus, MessageKind, AccessDecision,
                            ConversationRepository, MessageRepository, ReadMarkerRepository
    usecases/inbound/       OpenConversation, PostMessage, ListMessages, MarkRead, Unfollow, ListUnread,
                            ResolveAccess
    usecases/outbound/      SubjectResolverPort (exists, owner, state; permits by default)
    adapters/inbound/       ConversationEndpoint (messages too), ChatStreamEndpoint, ChatStreamRegistry (the open
                            connections: token-expiry close, heartbeat, matching events to connections),
                            SubjectLifecycleListener (phase 6: PlatformEvent → STATE_BOUND close / reopen)
    events/                 MessagePostedEvent, ReadMarkerChangedEvent (in-process, observed after commit; built in
                            phase 3 in place of a MessageBroadcastPort — see "Phase 3 as built")
  common/                   ChatProperties, error mapping (problem details), @Order-ed RestControllerAdvice
```

- Repositories stay in `domain` as Spring Data interfaces, as in base-workflow. Use cases depend on them
  directly; there are no extra repository ports.
- Roles and the display name come from `CurrentPrincipal`, so no RoleResolver port is needed.
- Composition root (`apps/processpuzzle-testbed-backend/.../composition`): `EntitySubjectAdapter`,
  `WorkflowInstanceSubjectAdapter` (phase 6), `ChatDefinitionReferenceAdapter`.
- `MediaSessionPort` is **not** created in v1. An empty port is speculative; `Message.kind` and the reserved
  SSE names are enough of a reservation.

### Layer verification — `arch-unit-maven-plugin`

The check is `com.societegenerale.commons:arch-unit-maven-plugin` **5.0.0**, released December 2025. It is
built for Java 25 and runs ArchUnit 1.4.1.

- **Where the rules live.** The mojo puts the project's test classpath on its class loader and finds
  `static ArchRule` fields on the configured class by reflection, so the rules sit in base-chat-backend's own
  `src/test/java` (`com.processpuzzle.chat.architecture.HexagonalLayerRules`). No extra artifact, and no
  plugin interface to implement.
- **Phase.** The execution is bound to `process-test-classes` (the default `process-classes` runs before the
  rule class is compiled), with `<configurableRules>` → `applyOn com.processpuzzle.chat`, scope `main`. A
  violation fails `mvn verify`, so it also fails nx `build` / `test` and CI. The plugin version is the root-pom
  property `arch-unit-maven-plugin.version`, so another library can adopt the check by adding the execution.
- **Rules** (fully qualified `com.processpuzzle.chat..` patterns, so another module's `domain` package is not
  mistaken for ours):
  1. `domain` depends on no `usecases`, no `adapters` and no transport type (`org.springframework.web`/`http`,
     `jakarta.servlet`, the generated `chat.api` / `chat.model` DTOs).
  2. `usecases` depends on no `adapters` and no transport type.
  3. `adapters.inbound` does not depend on `adapters.outbound`.
  4. `usecases.outbound` contains interfaces, plus the records and enums that are the data of their contract and the
     `PermitAll…` defaults (the first run of the rule caught `SubjectInfo`, a record, which settled the wording).
  5. `definition` does not depend on `conversation`.
  6. `conversation` does not depend on `definition.adapters` (it reads definitions through the definition
     use cases).
  Every rule allows an empty match, so a layer without classes yet is not a violation.
- **Checks on the check.** `HexagonalLayerRulesTest` runs each rule against deliberately violating fixture
  classes in test sources (the plugin checks main classes only) and a conforming layout against all of them.
  A rule that silently stops matching therefore fails a test. Verified once end to end by planting a violation
  in main: `mvn process-test-classes` failed on it.
- **Release builds.** Plugin dependencies resolve from Maven Central, and the rules are test classes, so
  `release-java-lib` needs no changes.

### Frontend — `libs/js-shared/base-chat-frontend` (`@processpuzzle/base-chat`)

Two layers under `src/lib`, as in base-event and base-calculation. `feature` depends on `domain`, never the
other way round.

| Layer | Contents |
| --- | --- |
| `domain/conversation/` | Types (`Conversation`, `ChatMessage`, `ChatPermissions`), `ChatService` (REST), `ChatStore` (signals: messages, unread, connectionState, permissions; optimistic send reconciled by `clientMessageId`) |
| `domain/stream/` | `ChatStreamService`: fetch-based SSE, bearer header, reconnect with backoff, per-conversation `seq` resume, `PUT …/stream/subscriptions` |
| `domain/definition/` | `ChatDefinition` types and the `BaseEntityDescriptor` used to author definitions through a metadata entity screen |
| `feature/panel/` | `pp-chat-panel`: message list (CDK virtual scroll), composer, empty `call-controls` slot |
| `feature/topics/` | `pp-chat-topics`: MANY list and "new topic" when the caller has a `createRoles` role |
| `feature/unread/` | `pp-chat-unread-badge` |
| `feature/definition/` | Routes for ChatDefinition authoring (metadata entity screen; a dedicated editor only if needed) |

- **Widgets.** Each widget is registered through `provideWidget` with a `propsSchema` and mirrored in the
  `base-widget-backend` seed YAML (`widget-contract.spec.ts` checks the two match). The panel takes its
  subject from its inputs, else from `ContextService`.
- **Transloco.** Scope `base_chat` with an `alias`.
- **Layer rule.** Lint the `domain → feature` direction with `no-restricted-imports` patterns in the lib's
  ESLint config. That is cheap, and it mirrors the Maven check on the backend.

### Phases

| Phase | Scope | Done when |
| --- | --- | --- |
| 0 | D1–D8 settled (below); amend the blueprint | Decisions done 2026-11-15; blueprint amendment pending |
| 1 Scaffold | Lib pair from `base-state-*` (13 registration files: tsconfig paths, nx release, package.json scripts, release scripts, workflows, root pom property and module, testbed pom + `implicitDependencies`), `@ApplicationModule`, `ModularityTests`, `arch-unit-maven-plugin` + `HexagonalLayerRules`, frontend `domain`/`feature` skeleton + import-direction lint, Sonar projects | CI green; module appears in `writesModuleStructureToTheLog`; a planted layer violation fails `mvn verify` |
| 2 Core (built) | Contract `base-chat-api.yaml` (openapi 3.0.3), definitions (seeded YAML, no release yet), conversations, messages with `seq`, access checks, entity subject adapter | API tests: open / post / page / 404-403-409 rules, idempotent post, concurrent posts give a gap-free `seq` |
| 3 Live (backend built) | Multiplexed stream, follow / read markers, unread, token-expiry close, heartbeat | Two browsers exchange messages; badges correct after reconnect; stream survives nginx for more than 60 s idle on stage |
| 4 Frontend (built, no e2e) | Store, stream service, panel + badge widgets, working-context fallback; testbed page on an entity | Playwright: two contexts chat on one Order |
| 5 Definitions | Release + import participants, `RolesReferenced`, authoring through a metadata entity screen (or editor), starter support | Designer adds a chat to Order without code; release refuses a dangling reference |
| 6 Topics & lifecycle | MANY multiplicity + topics widget; STATE_BOUND; workflow-instance adapter (needs base-workflow `PlatformEvent` on completion) | Protest topics under a Race in SailRC; workflow chat turns read-only on completion |
| 7 Hardening | Rate limit (in-memory token bucket), retention / erasure job, soft-delete moderation, Micrometer metrics, `LISTEN/NOTIFY` broadcaster if needed, load test | Metrics on stage; erasure removes body and name |
| 8 Calls | Separate project as in the blueprint (`MediaSessionPort` introduced here) | — |

### Phase 2 as built (2026-11-15)

The contract is `api-contracts/.../base-chat-api.yaml`; the three operations groups are definitions
(`GET` list, `GET`/`PUT` one — `PUT` creates or replaces the draft), conversations (find by subject, open, get)
and messages (page, post), the last two under one tag so one generated interface carries them.

Where the build differs from the plan above, and why:

- **Numbering without a locking read.** `seq` is taken with three bulk statements in the posting transaction:
  `lockIfStatus` (a no-op update that takes the row lock, and refuses a conversation that is not open),
  `advanceSeq`, `lastSeqOf`. The planned pessimistic read of the entity returns the instance the access check
  had already loaded, with a stale `lastSeq`. `Conversation.lastSeq` is not `updatable`, so a stale entity saved
  later cannot write an old number back. `PostMessageConcurrencyTest` runs 8 threads × 15 posts and 8 identical
  posts at once; removing the lock makes the second fail on the unique client id.
- **Definitions carry a channel from the start** (`DRAFT` / `RELEASED`, key `(orgKey, definitionKey, channel)`),
  so phase 5 only adds the release participant. The running application reads `RELEASED`; the seed importer
  writes both, as base-event's does. Nothing releases a designer's draft yet.
- **`AUTO_CREATE` cannot be opened by hand** (409): those conversations are created with their subject by the
  subject-lifecycle listener of phase 6. A definition may already be saved that way.
- **`includeSubjectOwner` has no effect on entities** until base-entity's `EntityObjectView` reports who created
  an object; the rule itself is implemented and tested with a resolver that does report an owner.
- **`SubjectType` is `ENTITY` only**; `WORKFLOW_INSTANCE` arrives with its adapter in phase 6.
- **Chat paths always need a token.** `SecurityConfig.CHAT_PATHS` is authenticated whatever
  `require-authentication` says, as the self-service paths are: a message has an author.
- **Facts.** Opening publishes `PlatformEvent(subjectType = "chat-conversation", CREATED)` through the host's
  durable event registry, and `chat-conversation` is declared as a `PlatformSubject` so the event catalog can
  name it. Messages publish nothing.
- **No read markers, unread counts, edit or delete**: phase 3 and later, as planned.
- **Named interfaces.** `chat :: port` (the outbound package) and `chat :: subject-type` (the one enum) are what
  the host's `ChatSubjectAdapter` may import.

### Phase 3 as built (2026-11-15)

Backend only; the frontend half of "two browsers see each other's messages" is phase 4. Added to the
contract: `PUT …/conversations/{id}/read`, `DELETE …/conversations/{id}/follow`, `GET …/unread`, and the stream
`GET …/chat/stream`, plus `following`, `lastReadSeq` and `unread` on `Conversation`.

- **Following is the marker.** A `ReadMarker` row `(conversation, user, lastReadSeq)` is the follow record, as
  planned (C2). Opening, posting and `PUT …/read` follow; `DELETE …/follow` unfollows. Unread is
  `lastSeq - lastReadSeq` — exact, because `seq` has no gaps — so there is no count query. A poster who was up to
  date moves to their own message; one who was behind stays behind. The marker only moves forward: it is created
  with one `insert … on conflict do nothing` and moved by locking its row (`select … for update`), comparing, and
  only then writing, so two tabs following at once need neither an exception nor a second transaction.
  An earlier version got this wrong twice, and `ReadMarkersConcurrencyTest` found both: it inserted with
  `repository.save`, which for an assigned composite key *merges* — a loser whose winner had already committed was
  told it had succeeded while the database kept the winner's lower number — and it inserted in a `REQUIRES_NEW`
  transaction, which holds a second connection while the first is still held, so a burst of first-time follows on
  a small pool starves itself (the test took 25 s instead of 1.5 s).
- **No `MessageBroadcastPort`.** The plan's port is replaced by two in-process events published by the use cases:
  `MessagePostedEvent` and `ReadMarkerChangedEvent`. `ChatStreamRegistry` (an inbound adapter, the owner of the
  connections) observes them with `@TransactionalEventListener(AFTER_COMMIT)`, so nobody is told of a message
  that rolled back. A second instance would relay the same two events (Postgres `LISTEN/NOTIFY`) in phase 7;
  nothing else changes.
- **One stream per user and tab, for everything they follow** (C3). `OpenChatStream` runs on the caller's own
  thread, where their token is, and returns a snapshot: the followed conversations they may read now. Delivery
  to another user happens on the poster's thread, where the recipient's roles cannot be checked — hence the
  snapshot. The stream ends at the **token's expiry** (new `CurrentPrincipal.tokenExpiresAt()` in
  `processpuzzle-core`) and in any case after `base-chat.stream.max-age-minutes` (15); the client reconnects with
  a fresh token, which takes the snapshot again. A role lost mid-stream therefore takes effect at the latest then;
  history, posting and every other call check afresh each time.
- **Resuming** is by explicit `after=<conversationId>:<seq>,…`, not `Last-Event-ID` (there is one sequence per
  conversation, not one per stream). Up to 200 missed messages per conversation are replayed right after
  `stream.ready`; beyond that a `resync` event tells the client to reload. A message that arrives live while the
  replay is read may be sent twice; clients ignore a `seq` they have.
- **Events:** `stream.ready`, `message.posted` (`id` = `conversationId:seq`), `read.updated`, `resync`, and a
  `: keepalive` comment every 25 s (`base-chat.stream.heartbeat-seconds`). The response carries
  `X-Accel-Buffering: no`, so nginx's default buffering does not hold events back.
- **The stream endpoint is hand-written** (a generated interface cannot return an `SseEmitter`); the contract
  documents it and the generated interface stays unimplemented. A refusal happens before the emitter exists, so it
  is a JSON error — the client must send `Accept: text/event-stream, application/json`.
- **Still to verify on stage:** the 60 s nginx read timeout against the 25 s heartbeat, and that events are not
  buffered. `nginx.conf` talks HTTP/1.0 to the backend (no `proxy_http_version`); streaming still works that way
  but it is worth setting 1.1 and an empty `Connection` header for `location /api/` when this is deployed.

### Phase 4 as built (2026-11-16)

`@processpuzzle/base-chat` now has its two layers filled: `domain/` (types, `ChatService`, `ChatStreamService`,
`ChatStore`, the identity port) and `feature/` (`pp-chat-panel`, `pp-chat-unread-badge`, their widget
registrations, the working-context bridge). The testbed has a Base Chat section whose Samples tab places the panel
on an order the visitor names.

Where the build differs from the plan, and why:

- **No dependency on the auth library.** The stream is read with `fetch`, which the HTTP interceptors do not see,
  so it needs the token itself. The library declares a `CHAT_IDENTITY` port (`accessToken()`, `userId()`) and the
  application supplies it where it is composed (`createChatIdentity()` in the testbed's `app.config.ts`, over the
  auth service), as it does for documents. `CHAT_FETCH` makes `fetch` replaceable in tests.
- **`ChatStore` is plain signals, not an NgRx `signalStore`**: one shared service holding two maps, with methods
  that are mostly async. It is `providedIn: 'root'` and shared by every panel and badge.
- **No CDK virtual scroll.** A page is 50 messages and the log scrolls; "Load earlier messages" prepends the next
  page. Virtualizing a log that is anchored to its bottom and grows at both ends is more machinery than a
  conversation of this size needs; revisit if a conversation is ever kept open across thousands of messages.
- **The SSE reader is ours** (`SseParser` + `fetch`) because `EventSource` cannot send `Authorization`. It
  reconnects at once when the server ends a healthy stream — which it does at the token's expiry — with a fresh
  token, and with jittered exponential backoff (1 s to 30 s) after a refusal or a network failure. A 70 s
  watchdog drops a connection that has gone silent although the server heartbeats every 25 s. Each (re)connect
  sends `after=` for the conversations whose messages are loaded.
- **Sending is optimistic and idempotent.** A message shows at once as pending; the answer or the same message on
  the stream replaces it by `clientMessageId`; a failed one stays, and retrying reuses the id, so a post that
  arrived but whose answer was lost is not stored twice.
- **History reloads replace, they do not merge.** Merging the newest page with messages held from an earlier visit
  left a gap between them; a reload keeps only what arrived on the stream meanwhile and is newer than the page.
- **The panel marks a conversation read when it is shown**, and for every message that arrives while it is. It
  does not look at tab visibility: a panel in a background tab reads as read.
- **Context fallback** is `CHAT_ACTIVE_SUBJECT`, provided by `provideChatWorkingContextSubject()` (the innermost
  active working context). It lives in `feature/` so that the panel and the whole domain layer stay free of
  base-entity; the panel injects the token optionally.
- **Two widgets, not three.** `chat-panel` and `chat-unread-badge` are registered in this library
  (`provideChatWidgets()`) and copied into the widget seed catalogue (category `Social`);
  `chat-widgets.contract.spec.ts` holds each copy to its registration, and `DefaultWidgetLoaderTest` lists the two
  keys. The topics widget comes with phase 6.

Not done, and why:

- **The Playwright check "two browsers chat on one Order"** — it needs the full stack with Keycloak, which was not
  run here. Everything it would exercise is covered below that level (the stream against a fake `fetch` with a
  real `ReadableStream`; the panel against a store; the backend's delivery to followers after commit), but the
  seam between a real browser, nginx and the stream is unverified. See `docs/e2e-plan.md`.
- **Designer authoring of chat definitions** is phase 5.

### Decisions (2026-11-15)

- **D1** No subject-less chats in v1. Every conversation has a subject.
- **D2** Unread counts use the follow model (C2).
- **D3** TOPIC scope becomes `multiplicity: MANY` on a subject binding (C1).
- **D4** Users may open topics at runtime if they hold one of the definition's `createRoles`.
- **D5** When a workflow instance completes, its chat becomes read-only. It is deleted only by retention.
- **D6** base-chat is a new library pair, `base-chat-backend` + `base-chat-frontend`, scaffolded from
  `base-state-*`.
- **D7** The backend follows the house hexagonal layout (adapters / usecases / domain, as in base-workflow).
  A Maven plugin enforces the layers in addition to `ModularityTests`; see *Layer verification*.
- **D8** The frontend uses `domain/` + `feature/` layers (as in base-event and base-calculation).
- Still open: retention / erasure rules per customer (needed before phase 7); SFU and hosting before phase 8.
