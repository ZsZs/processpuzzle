# Working Context (Active Entity) – Spec

2026-11-15

## Problem and goals

A user working inside one entity (a Race, a Customer, a Warehouse) should pick it once and then work inside it, instead of passing through List → Form every time. This spec introduces a generic **working context**: a metadata-declared entity that is selected upfront, shown in the status bar, and used to scope views and actions.

**Motivating case (SailRC).** Participants, Organisers, Field, Round and Result are embedded entities of Race. In the Conduct Race area the officer always works on one Race, so forcing the Race List → Race Form path on every visit is friction.

**Goals**

- Select the context entity once, from the normal List View.
- Show it persistently in the status bar; no page repeats it in its own header.
- Auto-scope all views and actions inside the context (add round, start/finish race) to the active entity.
- Everything is driven by metadata; no SailRC-specific code in the platform.

**Non-goals**

- Replacing the entity List and Form views; they stay for editing.
- Multi-user presence or locking (see Open decisions for write contention).
- A global search or command palette.

## UX design

The List View is the picker; the status bar is the permanent display of the active context. Switching goes through the same list, opened as a drawer.

### Selecting

- The context entity's list gets a primary row action **Set as active** (also double-click or Enter). **Open** keeps its meaning: go to the Form for editing.
- After selection the list collapses or slides away and the user lands on the first view that requires the context.
- Clicking the status bar chip reopens the list as a drawer with search and a **Recent** section on top (last 3 contexts, stored per user).

### Displaying

- The status bar chip shows the label, a state badge (for example Planned / Running / Finished) and an optional extra field (for example the current round).
- Chip content comes from the context's `display` metadata, not from code.
- Views inside the context do not repeat the context in their headers.

### Behaviour rules

| Rule | Detail |
| --- | --- |
| Context in the URL | `/ctx/:contextName/:id/...` is the source of truth. Refresh, deep links and a second device work without extra code. The last active id per context is kept in localStorage as a fallback only. |
| No redirect on missing context | A view that requires a context shows an empty state, "Select a <entity>", with the picker embedded. It never redirects to the list. |
| Confirm before switching | If the active entity matches `confirmSwitchWhen` (for example state Running), switching asks for confirmation. |
| Auto-scoping | Lists inside the context show only the active entity's data. "Add" actions need no entity selection. |
| Actions on the context | Transitions of the context entity's state machine (Start, Finish) appear as buttons in the chip's menu and on views that declare them. |
| Clearing | The chip menu offers **Clear context**; views that require it then show the empty state. |

### Responsive

- Desktop: the picker is a pinned or collapsible side panel, or the drawer.
- Tablet and phone (committee boat, on the water): full-height drawer, chip with a touch target of at least 48 px.

## Metadata model

A context is a metadata declaration pointing at an existing entity type. Views and actions opt in with `requiresContext`; nothing in the platform knows the word "Race".

```yaml
contexts:
  - name: race
    entity: Race
    parent: null                  # e.g. regatta, for nested contexts
    picker:
      view: race-list
      searchFields: [name, date]
      recent: 3
    display:
      label: "{{name}}"
      badge: state                # bound to the entity's state machine
      extra: "{{currentRound}}"
    confirmSwitchWhen:
      state: Running
    persist: url                  # url | url-local-storage

views:
  - name: participants-list
    requiresContext: race
    dataSource:
      context: race
      path: participants          # embedded collection of the active Race
  - name: add-round
    requiresContext: race
    action:
      context: race
      op: append
      path: rounds
  - name: race-results
    context: optional             # works with or without an active race
    dataSource:
      entity: Result
      filterByContext: race
```

| Key | Meaning |
| --- | --- |
| `contexts[].entity` | Entity type acting as context root. Any entity type may be declared. |
| `contexts[].picker.view` | Existing list view used as the picker (page and drawer). |
| `contexts[].display` | What the status bar chip renders. Templates plus a state-machine badge. |
| `contexts[].parent` | Optional parent context; selecting a parent clears its children. |
| `contexts[].confirmSwitchWhen` | Condition on the active entity that requires confirmation before switching. |
| `views[].requiresContext` | View needs this context; otherwise it shows the empty state. |
| `views[].context: optional` | View works without a context and offers a "filter by active context" toggle. |
| `dataSource.context` + `path` | Binds a list or form to an embedded collection of the active entity. |

## Frontend architecture

Four building blocks, all in the signals-first Angular stack and all reading the `contexts` metadata.

| Block | Responsibility |
| --- | --- |
| `ContextService` | Holds `active = signal<Record<string, EntityRef>>`. API: `select(name, ref)`, `clear(name)`, `current(name)`, `recent(name)`. Reads and writes the URL, falls back to localStorage, enforces `parent` rules (selecting a parent clears its children). |
| `requiresContext` guard / resolver | Resolves the active entity for a route. If none, it renders the empty state with the embedded picker. It never redirects. |
| `context-chip` widget | Status bar component. Renders `display` metadata, the state badge, the transitions menu and the recent-contexts drawer. Registered through the existing widget-embedding mechanism. |
| Data-source binding | Extends the metadata-driven list and form components with `{context, path}`, so they read and write an embedded collection of the active entity instead of a top-level entity set. |

**Route shape.** Context-bound routes live under `/ctx/:contextName/:id/...`. The router param is the source of truth; `ContextService` mirrors it into the signal. Routes of different contexts can be nested for hierarchical contexts.

**Switching flow.**

1. User picks an entity in the picker (page or drawer).
2. `ContextService.select` checks `confirmSwitchWhen` on the currently active entity and asks for confirmation if it matches.
3. The URL changes; the guard resolves the new entity; the chip and all bound views update from the signal.

**Testing.** The `@processpuzzle/e2e-testing` metadata-driven suite gets a context scenario: select, refresh, deep link, switch with confirmation, empty state.

## Backend architecture

The context needs no backend concept of its own. It is a frontend selection over entities that already exist. What the backend must offer is path-level access to embedded collections and state-machine transitions on the context entity.

### Embedded-collection API

A generic endpoint family on base-entity-backend, so that "add round" never sends the whole Race:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/entities/{type}/{id}/{path}` | List the embedded collection |
| POST | `/entities/{type}/{id}/{path}` | Append an item |
| PATCH | `/entities/{type}/{id}/{path}/{itemId}` | Partial update of one item |
| DELETE | `/entities/{type}/{id}/{path}/{itemId}` | Remove one item |

Implementation note: the embedded data lives in the JSONB layer, so these map to JSONB path operations (`jsonb_set`, array append) instead of read-modify-write of the whole document.

### Transitions on the context entity

- base-state-backend already exposes the available transitions per entity instance; the chip menu and context-aware views render buttons from that list.
- The same state value drives the chip badge. No separate status field.

### Concurrency

- A Race is one aggregate with many writers (results, rounds, participants, possibly several devices).
- Each embedded item carries a version; PATCH requires `If-Match` with that version, and the server answers 409 on mismatch.
- Appends are conflict-free by construction (the server assigns the item id).

## Open decisions

Three questions shape the data model and should be settled before implementation starts.

| Question | Recommendation | Why it matters now |
| --- | --- | --- |
| Nested contexts (Club → Regatta → Race)? | Support a `parent` key from the first version. Selecting a parent clears its children. | Retrofitting a hierarchy into the URL scheme and `ContextService` later is a breaking change. |
| Write contention on the shared aggregate? | Per-item versions with `If-Match`, path-level PATCH, no full-document replace. | Several devices (committee boat, shore) will write to the same Race during a start sequence. |
| Entities that make sense without a context? | Views declare `context: optional` and offer a "filter by active context" toggle. | Cross-race views (a person's results over all races) must not be forced into one race. |

- [ ] Decide whether the URL prefix `/ctx/:contextName/:id` is acceptable for existing routes or needs a configurable prefix.
- [ ] Decide how long the Recent list is kept and whether it is per device or per user.
- [ ] Decide whether Clear context is available on a Running race.

## SailRC mapping, phasing and acceptance

SailRC becomes the first consumer of the generic mechanism, expressed only as metadata.

### SailRC mapping

| Concept | SailRC value |
| --- | --- |
| Context entity | Race |
| Picker | Existing Race list view |
| Chip | Race name, state badge (Planned / Running / Finished), current round |
| Context views | Participants, Organisers, Field, Rounds, Results |
| Context actions | Add round; Start race and Finish race (state transitions) |
| Switch confirmation | When the active Race is Running |

### Phasing

1. **Context core:** metadata schema, `ContextService`, URL sync, guard with empty state, `context-chip` widget.
2. **Data-source binding:** `{context, path}` for lists and forms, plus the embedded-collection API with path-level PATCH.
3. **State actions:** transitions of the context entity as buttons, state badge in the chip.
4. **Hardening:** per-item versioning and 409 handling, nested contexts, `context: optional` views, e2e scenarios.
5. **Business Starter:** the sail race starter ships the Race context definition, so a new customer gets Conduct Race out of the box.

### Acceptance criteria

- [ ] A user selects a Race once in the list and reaches Conduct Race without opening the Race Form.
- [ ] The status bar always shows the active Race and its state; no Conduct view repeats it.
- [ ] Refreshing the browser or opening the URL on a second device restores the same Race.
- [ ] Opening a Conduct view with no active Race shows the empty state with the picker, not a redirect.
- [ ] Switching away from a Running Race asks for confirmation.
- [ ] Add round, Start and Finish act on the active Race without any race selection.
- [ ] A second, non-sailing context (for example Warehouse) works using metadata only, with no platform code change.
