# Nested collections inside a working context – design

2026-11-15 · part of phase 4, slice 3 (see [working-context-plan.md](working-context-plan.md))

Examples use a neutral domain: a **Warehouse** (the context entity) that embeds **Zones**, each of which embeds
**Shelves**. Nothing here is specific to any customer's domain; the platform reads descriptors and metadata only.

## The question, and what was missing from it

Phase 2 added item-level endpoints for embedded collections, and a screen (`ContextCollectionComponent`) that lists
one top-level collection of the active entity (the Warehouse's zones). Phase 4 then added the same endpoints one level
down (a zone's shelves), and the open question was how a screen gets from a zone to its shelves.

That question should not exist. **Embedded entities already have owner-in-the-URL addressing, to any depth.** In the
authoring routes an embedded row's screen is

`warehouse/<id>/details/zone/<zoneId>/details/shelf/<shelfId>/details`

and the machinery behind it is generic and already in `base-entity-frontend`:

| Piece | What it does |
| --- | --- |
| `readEmbeddedRouteChain` / `readEmbeddedBreadcrumb` | Reads the chain of entities (and ids) the URL walks through |
| `embeddedAggregateGuard` | Loads the root entity from the first level, resolves every row of the chain by key, redirects to the owner when a row is gone |
| `EmbeddedEntityHostComponent` | Hosts the child screens below the owner |
| `EmbeddedRouteContext` | Gives the hops from the root to the owner, the attribute and the row key |
| `EntityScreenResolver.embeddedChildrenOf` | Derives the child routes from the descriptors (containment is declared once, in the descriptors) |
| `EMBEDDED_COMPONENTS` control | Lists nested rows inside a form and navigates deeper in the URL on add or open |

The context's standalone collection screen (phase 2) bypassed all of this, so it had no URL chain and no way to go
deeper. The earlier proposal in this document (`contextPath` as an alternating path, parameter names in route
metadata) would have re-created, per route, what descriptors and routes already express. That was the wrong direction.

## What is actually different in a context

Two things, and only these:

1. **Where the chain starts.** The root level of an authoring URL is the entity's own route (`warehouse/<id>`). In a
   context the root is `ctx/<name>/<contextId>`. The chain reader and the guard recognise a level by route data
   (`entityName`) and `:entityId`; a context level declares `workingContext` and `:contextId` instead.
2. **How changes are saved.** `EmbeddedEntityService` implements the same `BaseEntityService` contract as every other
   store service, but its `add`, `update` and `delete` end in `accessor.stageRoot(...)`: the whole root document is
   staged and the **root form's Save** persists it. A context has no root form. Its point (spec, phase 2) is that a change
   sends the item, not the whole Warehouse, under item versions and without a document-wide save.

## Proposed design

### 1. The context level is an aggregate root in the chain

`readEmbeddedBreadcrumb` (and `declaredEntityName` / `declaresEntityId` next to it) learn that a route carrying
`workingContext` data and a `:contextId` parameter is a level `{ entityName: <the context's entity>, entityId }`. The
context's entity name comes from the context definition, which `WorkingContextService` already holds.

Nothing else in the chain changes: the guard loads the root by `levels[0]`, resolves the rest from the root payload,
and redirects to the owner (the context's landing view) when a row is missing.

### 2. Embedded child routes under the context routes

`workingContextRoutes(name, routes)` already mounts the views twice (with the entity, and as the empty state). It also
mounts the embedded child branches of the context's entity, the way `baseEntityRoutes` mounts them under an entity's
details route: derived from the descriptor (`embeddedChildrenOf`), not authored per route. A route that opts in needs
no nested metadata; containment is already in the descriptors.

### 3. A direct persistence variant

`DirectEmbeddedEntityService` implements `BaseEntityService` exactly like `EmbeddedEntityService`, with one change: the
commit calls the item endpoints (phase 2 and slice 3) instead of staging the root.

- The address is derived from the resolved `EmbeddedRouteContext`: `path` (hops, each an attribute and a row index) is
  turned into row ids with `rowId`, and `attrName` is the innermost collection. The result is a `CollectionAddress`.
- `add` appends the row; `update` sends a merge patch of the changed members (`mergePatch`) with the row's
  `itemVersion` as `If-Match`; `delete` removes it. After each call the root is reloaded, so the screens show what is
  stored.
- It is used where the aggregate root is a working context; everywhere else `EmbeddedEntityService` stays.

### 4. The collection screen

`ContextCollectionComponent` stays as the list of a context's top-level collection, but a row opens the existing embedded
form route below the context instead of an edit dialog. Add opens the form at `…/<child>/new/details`. The form's
`EMBEDDED_COMPONENTS` controls then list the nested rows and navigate deeper, exactly as in the authoring screens, so
**nested collections need no code of their own**.

The edit dialog (`ContextCollectionItemDialog`) can then be retired, or kept for a quick edit of a flat row. Open question 2.

## States

| Situation | Behaviour |
| --- | --- |
| No active context | The route's empty state with the picker (unchanged) |
| A row in the URL was deleted by someone else | The guard redirects to its owner, as in the authoring screens; no separate empty state |
| Conflict on a row | The existing conflict message, then a reload |
| Switching the context from inside a nested screen | The view stays; a chain below the old entity cannot be carried over, so the switch lands on the first view (existing rule) |

## Work

1. `readEmbeddedBreadcrumb` and the guard treat a context level as a root. Tests in `embedded-route-context.spec.ts`
   and the guard spec.
2. `workingContextRoutes` mounts the embedded child branches. Route spec.
3. `DirectEmbeddedEntityService` and the choice of service in the embedded facade when the root is a context. Unit tests,
   including the id-addressing of nested paths and the `If-Match` of an update.
4. `ContextCollectionComponent` row and add navigation. Component tests.
5. E2E: part of the context scenario (phase 4, last item).

## Decisions taken

1. **Immediate saves in a context.** A row is saved through the item endpoints when its form is saved; there is no document-wide Save.
2. **The edit dialog is retired.** A row, and Add, open the entity type's own embedded form below the context.
3. **A row with no `id` is not guessed at.** Rows stored before ids existed say "Save its <entity> once from its own form first". The entity's own form assigns the ids on that save.

## Built

| Piece | Where |
| --- | --- |
| A context level is a root of the URL chain | `embedded-route-context.ts` (`workingContext` on the level, `viaWorkingContext` on the context) |
| Embedded child branches below the context routes | `workingContextRoutes(name, routes, { entityName, embeddedChildren })`; the app route builder passes the children from the descriptors |
| Saving through the item endpoints | `DirectEmbeddedWriter`; `EmbeddedEntityService` uses it when the aggregate's root is a context |
| The list opens the row's form | `ContextCollectionComponent` (`<context url>/<item type>/<key>/details`, returns to the list) |
| No repeated crumb | the status bar's breadcrumb leaves out the context levels the context crumb already shows |

## Limits

- **Two levels.** The server addresses a collection one level below the entity's own (a zone's shelves). A row nested deeper is refused with a message.
- **The list screen** (`contextPath`) lists a top-level collection. Nested rows are reached through the embedded form's own list control, as in the authoring screens.
- **Not exercised in a browser yet.** Unit and component tests cover each piece; the end-to-end scenario is the remaining item.

## Trying it

The testbed seeds an app, **order-desk**, over its existing Order entity (embedded lines, state machine, calculated total). It needs no new entities.

1. Start the testbed (the app is created on startup if it is missing).
2. In the designer, open Application → App Definition → **order-desk**, then its **Preview** tab.
3. Orders → create or open a few orders (the generated forms), then **Set as active** on a row.
4. The status bar shows the order with its state and customer; its menu switches, opens or clears it.
5. **Lines** lists that order's lines. **Add** and a row open the line's form below the order and return to the list.
6. The header's **context-actions** buttons and the status bar's menu offer the order's transitions.
