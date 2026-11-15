# Nested collection screen – design

2026-11-15 · part of phase 4, slice 3 (see [working-context-plan.md](working-context-plan.md))

## Problem

The backend now addresses a collection nested in an item, such as the results of one round:
`…/{id}/rounds/{roundId}/results`. The screen that shows it has to know which round it is for. Today
`contextPath` on a route names one top-level collection (`rounds`), so the screen has nothing to pick the round by.

## Options

| | A. Master / detail on one page | B. Owner in the URL (recommended) | C. Owner in a query parameter |
| --- | --- | --- | --- |
| Shape | Rounds list on the left, results of the selected round on the right | `ctx/race/:id/rounds/:roundId/results` | `ctx/race/:id/results?round=<id>` |
| Source of truth | Component state | URL | URL |
| Deep link, refresh, second device | Needs extra code for the selection | Works with no extra code (same as the context itself) | Works, but the parameter is easy to drop |
| Route metadata | One route, plus a second list bound to the owner | One route with a path parameter; the owner list is an ordinary route | One route; the parameter name is not in the route definition |
| Fits the spec | Adds a second screen mode | Yes: `/ctx/:contextName/:id/...` already carries parameters | Partly |

**Recommendation: B.** It follows the rule the working context already uses (the URL is the source of truth),
needs no selection state, and reuses routes that already exist: the owner list is `rounds`, and each row links to
`rounds/<id>/results`.

## Design of B

### Metadata

An ENTITY route can already carry a path with `:name` parameters (the contract allows them, and the route builder
orders static segments before parameters). The nested route declares:

- `path`: `rounds/:roundId/results`
- `requiresContext`: `race`
- `contextPath`: `rounds/results` (the outer collection, then the nested one)
- `entityName`: `Result` (the items' type)

`contextPath` becomes a path: an alternating list of attributes, with the owner's id taken from the route parameter
of the same position. The first level is the active entity's collection, the second is the item named by
`:roundId`. The route parameter names are not part of the contract, only their position is.

### Screen

`ContextCollectionComponent` already lists a top-level collection, and gets add, edit and remove through the item
dialog. For a nested route it:

1. reads the owner ids from the route parameters, in order;
2. calls the nested service methods (`listNested`, `appendNested`, `patchNested`, `removeNested`) instead of the
   top-level ones;
3. shows the owner in its header, using the owner's title attribute from the outer list, so the user sees which round.

The owner's own version is not needed: the nested operations check the nested item's `itemVersion` and the
object-level race is handled by the backend.

### Linking from the owner list

The `rounds` list is a context collection too. Its rows need a way to open their nested collection. Proposal: a
route-level `rowLink` template, for example `results` (the child route under the current route), applied as a link
on the row's title. Without it the owner list shows rows and nothing else, which is also acceptable for a first slice.

### States

| Situation | Behaviour |
| --- | --- |
| No active context | The route's empty state with the picker (already how context routes behave) |
| Owner item does not exist (deleted, or a stale link) | An empty state "No round '<id>'" with a link back to the owner list; not a redirect |
| Owner exists, nested collection empty | The empty text of the collection screen |
| Conflict on an edit of a nested item | The existing conflict message, and a reload |

### Context switch

Switching the active race from inside `rounds/:roundId/results` follows the existing switch rule: the view stays,
and the owner id cannot be carried over. The switch lands on the first view of the new race, as it does today.

## Work

1. Frontend route: `contextPath` as a path; the renderer passes the parameter names through route data.
2. `ContextCollectionComponent`: owner ids from route params, nested service calls, owner header, owner-not-found state.
3. Owner list: optional `rowLink`.
4. Tests: component tests for the nested calls and the owner-not-found state; renderer test for the path; validator
   check that every `:param` of the route path appears in `contextPath`'s order.
5. E2E: part of the context scenario (phase 4, last item).

## Open questions

1. Should `contextPath` stay a single attribute name for top-level collections, and become a path only when nested?
   Proposed: yes, a single name is a path of one.
2. Is `rowLink` needed for this slice, or can the owner list stay as it is and the user reach a nested screen by URL?
   Proposed: include it; without it the nested screen is hard to find.
3. Should the validator check that the route path's parameters match `contextPath`? Proposed: yes, as a warning first.
