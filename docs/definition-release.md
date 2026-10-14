# Releasing an organization's definitions

A designer edits **drafts** of every definition. The running application reads only **released**
definitions. One **release** validates every draft against its references and the organization's live
data, then makes all of them live at once, or none.

This replaces "live on save". Until now only the application had a draft and a published revision; every
other definition went live the moment it was saved, and a published application also mounted the
current *draft* of its modules.

## Decisions (2026-10-11)

- **Storage.** A definition is stored twice: a `DRAFT` row and a `RELEASED` row, told apart by a `channel`
  column. There is no history and no rollback in v1; the release record keeps a hash of what each release
  carried, not the content.
- **Live data.** A release checks for changes that live data would not survive and **refuses** them.
  Pinning running work to old revisions, and migrating data, are postponed.
- **Preview** shows the drafts, read-only on live data.
- **One application per organization**, so the organization's release is the application's release.
- **Scope.** App and modules; entities, state machines (with diagrams) and rules; workflows with their
  catalogue (roles, tasks, artifacts, tools) and events; documents. Widget definitions stay out: nothing
  reads them at run time, rendering uses the compiled widget registry.
- **Documents are checked, not released** (2026-10-14). A document is content with an editorial workflow of
  its own — editor and publisher roles, a public read path, Publish and Unpublish per translation — so a
  release neither publishes nor withdraws one. Starters carry no documents yet; a document import
  participant comes when a starter needs one.

## The release machinery

### The SPI — `processpuzzle-core`, `core.definition`

Beside `DefinitionImportParticipant`:

| Type | Role |
| --- | --- |
| `ReleaseParticipant` | Implemented by each feature for its own kind: `drafts`, `pending`, `references`, `check`, `release`. |
| `PendingChange` | One definition whose draft differs from its released row: `ADDED`, `CHANGED` or `REMOVED`, with the draft's canonical JSON. |
| `DefinitionReference` | One definition naming another, `(kind, key) → (kind, key)`. *Optional* references only warn when unresolved. |
| `ReleaseCandidate` | The organization as it would be after the release, so a participant's `check` can ask about other kinds. |
| `ReleaseProblem` | An `ERROR` refuses the release; a `WARNING` lets it through once confirmed. |
| `Channel` | `DRAFT` / `RELEASED`, and the `revision` query parameter that names one. |
| `ChannelBackfill` | Moves an existing definition table onto channels once; see *Backfill*. |

`references()` doubles as the answer to "who refers to this definition?", which the definition delete
guards (business-starters design §10, item 4) will ask.

### The orchestrator — `base-release-backend`, module `release`

`ReleaseOrganization`, after the pattern of the starter importer:

1. Collect the participants, in `DefinitionKinds` order: entity 10 … workflow 60, module 65, app 70.
2. Ask each for its drafts and pending changes, then resolve every reference of every draft against all
   drafts. An unresolved required reference is an error, an unresolved optional one a warning. A
   reference into a kind no participant releases is not checked: that kind still goes live on save.
3. Run each participant's `check`.
4. Refuse with **409** and the plan on any error, on warnings the caller has not confirmed, and when
   nothing is pending.
5. Otherwise call `release()` on every participant, in order, in **one transaction** (the platform has a
   single datasource), and record the release.

The plan is made again inside that transaction, not taken from the dry run the designer looked at, so
what is checked is what is released.

Tables: `org_releases (org_key, release_number, released_at, released_by)` and
`org_release_items (org_key, release_number, sequence_no, kind, definition_key, change_type, content_hash)`.
The hash is SHA-256 of the definition's canonical JSON (`CanonicalJson`). A concurrent release fails on
the unique `(org_key, release_number)`.

**History.** `org_release_contents (item_id, org_key, content)` keeps that canonical JSON itself, one row
per item — a table of its own, so listing releases does not read every definition's content. A release
records only what it changed, so what an organization ran at release *n* is the newest item of each
definition up to *n*, removals left out: `ReleaseHistory.contentAt(orgKey, n)`. Not exposed yet; it is
what a diff between releases, a return to one, or running work bound to its release will stand on.
Only the latest release is what the runtime reads — the `RELEASED` rows.

The contract is `base-release-api.yaml`, under `/organizations/{orgKey}/releases`:

| Operation | |
| --- | --- |
| `GET  .../releases/pending` | The dry run: pending changes and problems. |
| `POST .../releases` | Release; `{ acknowledgeWarnings }` confirms the warnings. 201 with the release, or 409 with the plan. |
| `GET  .../releases` | The history, newest first. |

All three require design rights (`OrganizationGuard.requireDesign`). The module's `TenantDataCleaner`
erases both tables with the tenant.

## Per kind

| Kind | Draft / released storage | Participant | Status |
| --- | --- | --- | --- |
| app | `AppDefinition` keeps `draftGraph` and `publishedGraph` on one row; releasing is `publish()` | `AppReleaseParticipant` (base-app) | step 1 |
| module | `app_modules.channel`, primary key `(org_key, module_key, channel)` | `ModuleReleaseParticipant` (base-app) | step 1 |
| entity | `base_entity_definition.channel`, unique `(org_key, code, channel)`; attributes follow their definition | `EntityDefinitionReleaseParticipant` (base-entity) | step 2 |
| state | `channel` on `state_machine_definitions` and `diagram_definitions`, keys `(org_key, entity_name, channel)`; a machine and its layout are released together, as one definition | `StateMachineReleaseParticipant` (base-state) | step 2 |
| rule | `rule_definitions.channel`, key `(org_key, id, channel)`; `rule_definition_fields` joins on all three | `RuleReleaseParticipant` (base-rule) | step 2 |
| event | `event_definition.channel`, key `(org_key, id, channel)` | `EventDefinitionReleaseParticipant` (base-event) | step 3a |
| artifact, role, tool, task | `channel` on `workflow_artifact_definition`, `workflow_role_definition`, `workflow_tool_definition`, `workflow_task_definition`, keys `(org_key, id, channel)` | `ArtifactReleaseParticipant`, `RoleReleaseParticipant`, `ToolReleaseParticipant`, `TaskReleaseParticipant` (base-workflow) | step 3b |
| workflow | `workflow_definition.channel` and `workflow_diagram.channel`; a workflow and its diagram are released together, as one definition | `WorkflowReleaseParticipant` (base-workflow) | step 3b |
| document | not released: `published_documents` keeps each translation's own published snapshot | `DocumentReleaseParticipant` (base-document), checks only | step 4 |

**Designer CRUD reads and writes the draft only.** Endpoints are unchanged; reads take an optional
`revision=draft|released`, `draft` by default. Reading the draft requires design rights, the released row
membership. **Run-time readers read the released row.**

### App and modules (step 1)

- A participant compares the canonical JSON of the draft with that of the released row, so a save that
  changes nothing is not pending.
- The app's `check` applies the same validation a save does, to the apps with pending changes only; its
  errors refuse the release.
- An app's routes refer to the entities and documents they render (required), its module mounts to the
  modules (optional: a mount of a missing module shows nothing, as before).
- **The module leak is closed.** `GET .../layout` carries the mounted modules inline, in
  `mountedModules`: the released rows with the published layout, the drafts with `draft=true`, each
  role-filtered. The runtime host builds a mount from those and never fetches a module, so the live
  application cannot pick up a module's draft.
- The app form's own Publish button is gone: publishing is the release, from the designer's **Release**
  section. `POST .../app-definitions/{appId}/publish` still exists for deployments without
  base-release-backend.

### Backfill

Hibernate's `ddl-auto: update` adds a `channel` column but never changes a primary key, so a released
copy would collide with its draft. Each feature runs `ChannelBackfill` once at startup, ahead of its seed
loaders: rows become `DRAFT`, each gets a `RELEASED` copy, and the primary key widens. An existing
organization therefore runs exactly as before. Idempotent: a table whose key already includes `channel`
is left alone. Flyway stays deferred to the production move.

**Entity definitions** have a generated UUID key with their attributes hanging off it, so their copy is made
in Java (`BaseEntityDefinition.releasedCopyOf`, the same one a release makes) by
`EntityDefinitionChannelBackfill`. It drops the old unique key on `(org_key, code)`, which would refuse every
copy and which Hibernate never drops; that key's presence is also what marks the table as not yet migrated,
so a draft created later is never released by a restart. A release updates the released row in place,
matching attributes by code — never clear-and-re-add, because Hibernate flushes inserts before orphan
deletes. `DefaultEntityLoader` releases what it seeds at once, since its sample objects are validated
against the released definition.

### Events (step 3a)

- **Who reads which row.** The platform-fact matcher (`PlatformEventCatalogListener`) and the thrown-event
  republisher (`ThrownEventCatalogListener`, via `FindEventDefinition.released`) read the released catalog.
  Designer CRUD, import and the list and get endpoints (`revision`, `releaseState`) work on drafts, and so does
  the workflow's `EventCatalogPort` (`FindEventDefinition.exists` / `kindOf`): it is asked while a designer saves
  a workflow, which is designed against drafts.
- **References.** A SYSTEM event refers to the entity type its facts are about (`subjectType`, an entity
  code). An event holds no data of its own, so it has no compatibility checks; a running workflow that still
  waits for a removed event is a reference of the workflow, checked on its side (step 3b).
- **Seeding.** `DefaultEventDefinitionImporter` releases what its files wrote, and only that
  (`ImportOutcome.written`).
- **Backfill.** `EventChannelBackfill`: the generic `ChannelBackfill`, since the table has no children and no
  foreign key onto it.

### Workflows and their catalogue (step 3b)

- **One mechanism, five kinds.** `DefinitionReleaseParticipant` (base-workflow) does what every kind does — pending by
  a canonical fingerprint of the authored fields, release in place, removal — and each kind names its rows, content,
  copy and references. Each catalogue item is its own entry in the Release section, since workflows share them; a
  workflow's entry covers its diagram, so a moved node is a pending change.
- **Who reads which row.** The running application reads released rows: `ResolveWorkflowUseCase` (every start, task
  completion, assignment, skip, timer and catch event), `TriggeredStartListener`, `InputArtifactStartListener`,
  `StartEventAdmission`, `FireStartTimerUseCase`, `StartTimerScheduleReconciler` and `ToolStepExecutor`. Designer CRUD,
  import, export, the validators and `CatalogReferenceScanner` work on drafts; list and get take `revision` and
  report `releaseState`.
- **What follows the release instead of a save.** The organization's directory (Keycloak groups for roles): the
  release announces `RoleDefinitionChangedEvent` / `RoleDefinitionDeletedEvent` for the roles it changes and removes,
  and `SyncRoleDirectory` reconciles released roles only. The start timers: the release announces
  `WorkflowChangedEvent`, and the reconciler reads the released workflow. Both listeners run after the release
  commits. A designer's save announces neither any longer.
- **References.** A workflow: the workflow it extends, its roles, artifacts, tools and tasks, the roles performing its
  tasks and authorized to start it, the artifacts a start requires, and its events (triggering, thrown, caught). A
  task: its roles, input and output artifacts, pre- and postcondition rules, and the tools of its steps. A role: the
  artifacts it is responsible for. An artifact: its entity type (by name) and its state machine.
- **Checks.** Error: a workflow removed while instances run (ACTIVE or SUSPENDED); a task removed while a task
  instance has it open (PENDING, ACTIVE or BLOCKED). Running instances are not pinned to their revision: they continue
  on the released definition (phase 3).
- **Seeding.** `DefaultWorkflowImporter` releases what its file wrote (`ImportOutcome.written`, by kind) through
  `ReleaseImportedWorkflows`, catalogue first.
- **Backfill.** `WorkflowChannelBackfill`: the generic `ChannelBackfill` over the six tables.

### Documents (step 4)

`DocumentReleaseParticipant` takes part in every release without releasing anything: nothing is pending,
and its `release` does nothing.

- **Referable** are the documents with a published translation, by id and by slug, since that is what the
  live application can show. An app route to a document nobody has published therefore refuses the
  release, as a route to a missing entity type does. Until step 4, references into documents were not
  checked at all.
- **References** are the entity types the document's input ports are bound to, optional: the host resolves
  a port's binding, and a port of a missing type shows nothing.
- **Check:** a warning for every document with a translation whose draft is not what readers are served
  (never published, or edited since), so whoever releases knows the live application still shows the
  older text, or none.

### Compatibility checks (`check`), from step 2

- **Entity** (only while objects of the type exist). Error: the definition removed; a required attribute
  added without a default; an attribute's value kind or multiplicity changed; a switch between embedded
  and stand-alone. Warning: an attribute removed; an attribute that becomes required. A rename is a
  removal and an addition. Entity objects are not migrated between releases.
- **State** (only while objects of the type exist). Error: a state removed while objects are in it; a changed
  state attribute. Warning: the machine removed — its objects keep their state, ungoverned. A moved layout is
  pending but never a problem. A renamed state is a removal and an addition.
- **Workflow.** Error: a workflow removed while instances run, or a task removed while an active task
  instance uses it.
- **References.** App routes → entity / document / module; rule context → entity; state machine →
  entity; workflow → events, roles, tasks, tools, artifacts, rules.

### Release state of a draft

Each definition endpoint reports a read-only `releaseState` on the drafts it answers with — entity
definitions, state machines and rules for now: `NEW` (never released), `CHANGED` (differs from the released
row) or `RELEASED` (what the running application uses). It is derived from the participant's own `pending`
(`ReleaseState.ofDrafts` in `core.definition`), so a list and the Release section cannot disagree. A released
definition whose draft was deleted has no row in a list; the Release section shows it as removed. A released
row carries no state, and the fingerprints leave the field out.

**What it replaced.** An entity definition's `status` used to be the only way to keep a definition off the
running application (`DRAFT`). A release does that now, so `status` is the designer's lifecycle decision
only — `ACTIVE` or `DEPRECATED`. `DRAFT` is still accepted and stored as `ACTIVE`; a definition stored with it
before this was never live, so `EntityDefinitionStatusMigration` makes its draft `ACTIVE` and removes the
released copy the channel backfill gave it — it then waits for its first release, as it waited before.

## Frontend

- **Release section** in `@processpuzzle/base-release`, mounted by `@processpuzzle/design` at `/design/release`: the pending changes by kind, the
  problems (errors block, warnings need confirming), Publish, and the release history.
- **The live application** renders the published layout and its inline modules.
- **Revision of the reads (step 2).** `DEFINITION_REVISION` (`@processpuzzle/util`) answers, per read,
  `draft` or `released`. Definition readers mark their GETs with `definitionRead()` — entity definitions,
  state machines, diagrams, rules (via `BaseEntityRestService.readContext()`) — and the opt-in
  `definitionRevisionInterceptor` adds `revision=<answer>` to them.
  - **`draft` unless the host says otherwise**: that is what the endpoints serve without the parameter,
    so a host that has not wired it up reads, and saves back, the drafts as before.
  - The custom application answers `draft` on its designer route and `released` elsewhere, and does a
    **full page load** when crossing between them. The root caches (entity registries, governed
    entities, the rule engine) are then never filled from both revisions; resetting them in place is
    not reliable, since the dynamic screens' injection tokens are cached at the root too.
- **Release column.** The entity-definition, state-machine and rule lists and forms show `releaseState`,
  read-only (`ReleaseState` in `@processpuzzle/util`); the entity-definition status offers Active and
  Deprecated only. From step 3, so do the workflow, role, task, artifact, tool and event-definition screens, whose
  services (with the workflow diagram's) mark their reads with `definitionRead()` too.
- **Read-only Preview.** `ENTITY_PREVIEW_READ_ONLY` (`@processpuzzle/base-entity`), provided `true` by a
  host showing drafts, makes `EntityPreviewMode` disable New, Save and Delete on the screens of
  definition-built entity types — they show live objects through draft definitions. The designer's own,
  compiled screens stay editable.

## Starters and seeding

A starter import writes drafts. **Sign-up seeding** (the custom backend's `SeedOrganization`) releases them
right after installing, in the same transaction — a starter is a vetted set and should work out of the box
(`ReleaseOrganization.provision`, which skips the design-rights check). **A designer installing a starter**
gets drafts like any other edit, and releases them from the Release section. A blank seed stays
unreleased, which shows the "not published yet" page.

**Rules.** `EvaluateObject` — the rule endpoint, state guards, workflow conditions — reads the released rules.
The engine's in-memory registry holds released rules only: a designer's save no longer touches it, and
`RuleReleaser` registers or unregisters after a release commits. A rule's reference is the rule it `extends`;
rules hold no data, so a release checks nothing else about them. `RuleChannelBackfill` drops the fields' old
foreign key first — the database refuses to drop the primary key it depends on — and adds the fields'
`channel` column itself, since `ddl-auto` cannot add it `not null` to a table with rows. `SampleRuleLoader`
releases the rules its file wrote.

**Run-time readers of state machines** — firing a transition, an object's state, starting the machine of a new
object, the startup consistency check, and `StateOperationApi` — read the released machine. `DefaultStateImporter`
releases the machines its file wrote, and only those, through `StateMachineReleaser`.

**Run-time readers of entity definitions** — object create and update, payload validation, RSQL paths,
workflow labels, the AI subject directory — read the released row through `EntityDefinitionLookupPort`.
`EntityAttributeQuery` answers about the released row unless asked for a `Channel`: base-state's
`StateMachineTopologyValidator` checks a state machine draft against the entity type's draft, which it will
be released with.

## Delivery

1. **Machinery + app and modules** — the SPI, base-release-backend, the contract, the Release section,
   the app and module participants, the module channel and the module leak. *This document.*
2. **Entities, states, rules** — channels, backfill, participants with checks, run-time readers, the
   frontend revision token, a read-only Preview.
3. **Workflows, catalogue, events.**
4. **Documents** — checked, not released; see [Documents](#documents-step-4). The starter install's
   release is in place since step 2; starters carry no documents yet.

Each is a core PR, then library releases, then a biz bump. The custom backend adds base-release-backend
in step 1, and an application-level test asserts that every definition table with a `channel` column has
a participant.
