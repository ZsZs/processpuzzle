# base-calendar – Design Plan

2026-11-19 · status: phases 1 (scaffold) and 2 (core API) built, see §11 and §12

Smaller organizations need a shared calendar as much as they need lists and forms: who is on duty, when the
meeting room is free, which boat is booked for Saturday. `base-calendar` gives a ProcessPuzzle application any
number of calendars, each configured by metadata and placed as widgets, for two uses at once:

- **Team calendars** – meetings, shifts, holidays; entries may overlap.
- **Resource calendars** – rooms, boats, equipment; bookings are checked against a capacity.

## 1. Decisions

| # | Decision | Reason |
| --- | --- | --- |
| D1 | A feature pair `base-calendar-backend` (module `calendar`) + `@processpuzzle/base-calendar`, placed through widgets – not a widget inside `base-widget-frontend` | Calendars are persisted, access-controlled data with their own API; `base-widget-frontend` holds widgets with no backend of their own. |
| D2 | **Configuration is split**: `CalendarDefinition` (backend, org-scoped, released) holds policy and booking rules; widget props hold presentation only | Visibility and booking rules must be enforced by the server. A prop is client-side and an anonymous viewer could edit it. One definition may be placed many times, with different views. |
| D3 | **Separate from base-chat**, integrated through chat's subject binding and widget placement | Feature libs have no compile dependency on each other ([feature-lib rule](../backend-library-dependencies.md)); each is used without the other (public booking calendar without chat, Order chat without calendar); they are released at their own pace. See §8. |
| D4 | **FullCalendar 7** (MIT core: `daygrid`, `timegrid`, `list`, `interaction`) for rendering, wrapped in one component | Covers day / week / month / list views, drag and resize. `@fullcalendar/angular` 7.1.5 declares Angular 16–22 as peers and uses `temporal-polyfill`. The commercial resource / timeline views are not needed: a resource is a calendar of its own (§3). |
| D5 | **Recurrence is expanded on the server** (RFC 5545 `RRULE` / `EXDATE`, `lib-recur` or `ical4j`) | DST, exceptions and range queries in one place; the client only renders occurrences. Recurrence arithmetic is not written by hand. |
| D6 | **References instead of embedded knowledge**: an entry links to platform artifacts (entity object, list, state, rule, workflow, document, conversation) by kind and key; existence is checked through an outbound port | The calendar learns nothing about other features; adapters live in the composition root, as for chat. |
| D7 | **Approval is not a calendar feature**: an entry is a `PlatformSubject`, so base-state can govern its states and base-workflow can start an approval from its events | Keeps the calendar small and reuses the platform's own machinery. |
| D8 | **Attendees with RSVP**: users (with a response each) and roles (an invitation to everyone holding it, no per-person response); no external e-mail attendees | Meetings need to know who comes. External attendees need an outbound mail channel the platform does not have (§10). |
| D9 | **Time zone is an organization setting**, not a calendar field; the calendar reads it through an outbound port | One organization works in one zone; a per-calendar zone would only allow inconsistency. |
| D10 | **The `.ics` feed is for PUBLIC calendars only** in the first version; per-user secret feed tokens come later | A subscription URL cannot carry a bearer token. |
| D11 | **No reminders** in this plan | They need a notification channel the platform does not have yet. |

## 2. Model

### CalendarDefinition (org-scoped, `DRAFT` / `RELEASED`)

```yaml
key: meeting-room-a
name: Meeting room A
kind: RESOURCE                 # TEAM | RESOURCE
                               # no timeZone: the organization's setting applies (D9, §5a)
policy:
  visibility: ROLES            # PUBLIC | AUTHENTICATED | ROLES
  readRoles: [staff]           # ROLES only
  bookRoles: [staff]           # may create entries and edit their own
  manageRoles: [office-admin]  # may edit and cancel anybody's entries
  publicDetail: BUSY_ONLY      # FULL | BUSY_ONLY – what a reader without bookRoles sees
booking:
  allowedKinds: [TIMED, ALL_DAY, RECURRING]
  capacity: 1                  # RESOURCE only; concurrent bookings allowed
  slotMinutes: 30
  minDurationMinutes: 30
  maxDurationMinutes: 480
  horizonDays: 180             # how far ahead a booking may be made; null = unlimited
resource:                      # RESOURCE only, optional
  ref: { kind: ENTITY_OBJECT, definition: Room, id: 3f2c… }
entryStateMachine: null        # optional base-state machine name governing entries (D7)
entryDetailWidgets: []         # WidgetInstance[] rendered in the entry details panel (§8)
colors: { default: '#186fce', categories: { training: '#5cdacf' } }
```

### CalendarEntry

| Field | Notes |
| --- | --- |
| `id`, `orgKey`, `calendarKey` | |
| `kind` | `TIMED`, `ALL_DAY` or `RECURRING` |
| `title`, `description` (markdown), `location`, `category` | |
| `start`, `end`, `timeZone` | TIMED: UTC instants plus the IANA zone they were entered in, so a recurring 09:00 stays 09:00 across DST |
| `startDate`, `endDate` | ALL_DAY: local dates, `endDate` exclusive, no zone |
| `rrule`, `exdates[]` | RECURRING: RFC 5545 text; `UNTIL` / `COUNT` bound it |
| `visibility` | `PUBLIC` or `PRIVATE`; a private entry shows as "busy" to everyone but the organizer and `manageRoles` |
| `organizerSub`, `organizerDisplayName` | Snapshot from `CurrentPrincipal`, as chat's author (no Keycloak admin lookups) |
| `attendees[]` | `{ type: USER \| ROLE, principal, displayName, required, response, respondedAt }` – see §4a |
| `quantity` | RESOURCE: units booked against `capacity`, default 1 |
| `references[]` | `{ kind, ref, label }` – see §4 |
| `state` | Written by base-state when `entryStateMachine` is set; otherwise null |
| `version` | Optimistic locking, as other PUT resources |

### Tables

| Table | Columns | Constraints and notes |
| --- | --- | --- |
| `calendar_definition` | key, org_key, channel, content (JSON) | key `(org_key, key, channel)`, as chat |
| `calendar_entry` | id, org_key, calendar_key, kind, title, …, start_utc, end_utc, time_zone, start_date, end_date, rrule, series_end_utc, visibility, organizer_sub, quantity, state, version | index `(org_key, calendar_key, start_utc, series_end_utc)` for range queries; `series_end_utc` is null for open series |
| `calendar_entry_exception` | entry_id, recurrence_id, cancelled, override (JSON) | PK `(entry_id, recurrence_id)`; one changed or cancelled occurrence |
| `calendar_entry_reference` | entry_id, kind, ref, label | |
| `calendar_entry_attendee` | entry_id, type, principal, display_name, required, response, responded_at | PK `(entry_id, type, principal)`; index `(org_key, type, principal)` for "my calendar"; `principal` is a user `sub` or a role name |
| `calendar_lock` | org_key, calendar_key | one row per resource calendar; locked while a booking is checked (§5) |

## 3. Booking kinds and recurrence

- **TIMED** – start and end instant. **ALL_DAY** – one or more whole days.
- **RECURRING** – a TIMED or ALL_DAY template plus `rrule`. The editor offers the usual presets (daily, weekly on
  chosen days, monthly by date or weekday, yearly, end after N / on date) and writes the RRULE; free-text RRULE is
  not offered.
- **Editing an occurrence**: "only this one" writes a `calendar_entry_exception`; "all" edits the series.
  "This and following" splits the series (old `UNTIL`, new entry) – phase 4.
- **Occurrences** are computed per query window: `GET …/occurrences?from&to&calendars=a,b` expands series, applies
  exceptions and returns flat occurrences `{ entryId, recurrenceId, start, end, allDay, title, … }`.
  Windows are capped (e.g. 100 days) so an open series cannot be expanded without limit.

## 4. References to platform artifacts

An entry can link what it is about or what it needs:

| `kind` | `ref` | Rendered as |
| --- | --- | --- |
| `ENTITY_OBJECT` | `{ definition, id }` | Link to the object's details screen |
| `ENTITY_LIST` | `{ definition, rsql? }` | Link to the list screen, optionally filtered |
| `STATE` | `{ machine, state }` | State chip, link to the state machine |
| `RULE` | `{ ruleId }` | Link to the rule |
| `WORKFLOW_DEFINITION` / `WORKFLOW_INSTANCE` | `{ id }` | Link to the definition or the instance monitor |
| `DOCUMENT` | `{ key }` | Link to the document viewer |
| `CONVERSATION` | `{ id }` | Link to the chat (usually the entry's own conversation, §8) |

- **Backend:** `CalendarReferencePort` (outbound, `exists(kind, ref)`), permitting by default; adapters in the
  composition root. Checked when an entry is saved; a dangling reference is a 422.
- **Frontend:** a `CalendarReferenceLinkResolver` turns a reference into a route; entity kinds go through
  `EntityScreenResolver`. Unknown kinds render as plain labels.

## 4a. Attendees

- **Who can be invited.** A `USER` attendee is a user of the organization; a `ROLE` attendee invites everyone
  holding that realm role (a team meeting). A role attendee gets no per-person response – only USER attendees RSVP.
  A person who belongs to an invited role can add themself as a USER attendee to answer.
- **Picking users.** org-admin's `/admin/users` search is for administrators. The calendar declares a
  `CalendarDirectoryPort` (`search(orgKey, text)` → `{ sub, displayName }`, at most 20 results); the composition
  root adapts it to the identity provider's user search. It returns name and subject only, no e-mail or roles.
  Role attendees are picked from the role catalog (`GET /organizations/{orgKey}/role-catalog`).
- **Snapshots.** `displayName` is stored at invitation time, like the organizer; the directory is not queried
  when entries are read.
- **RSVP.** `PUT …/entries/{id}/attendance` with `ACCEPTED | TENTATIVE | DECLINED`, allowed for the attendee
  themself. New attendees start at `NEEDS_ACTION`; the organizer is an attendee with `ACCEPTED`. For a series the
  response applies to the whole series; answering for one occurrence writes it into that occurrence's exception
  (phase 4).
- **Changes that reset responses.** Moving the time of an entry (or series) resets every USER response to
  `NEEDS_ACTION`; editing title, description or references does not.
- **Access.** An attendee (USER, or holder of a ROLE attendee) may read the entry with full detail even when it is
  `PRIVATE` or the calendar's `publicDetail` is `BUSY_ONLY`, and may RSVP, without `bookRoles`. Only the organizer
  and `manageRoles` edit the attendee list. Being an attendee grants nothing on the calendar's other entries.
- **Capacity.** Attendees do not count against a resource's capacity; `quantity` does.
- **"My calendar".** `GET …/calendar/my-occurrences?from&to` returns the occurrences, across all calendars, where
  the caller is the organizer or an attendee (directly or through a role). The `calendar` widget shows them with
  the prop `includeMine`; it is also how invitees find out about an invitation until notifications exist.
- **Out of scope:** external (e-mail) attendees, invitation and change notifications (D11), free / busy hints in
  the attendee picker (phase 6 candidate).
- **Erasure.** `DELETE …/calendar/users/{userSub}` anonymizes the user's organizer and attendee snapshots, as
  chat's erasure endpoint does.

## 5. Access and booking rules

- **Visibility** is decided in the use case, not the filter chain: the calendar read paths
  (`GET …/calendars/{key}`, `…/occurrences`) are `permitAll` in `SecurityConfig`, so a PUBLIC calendar works for
  an anonymous visitor regardless of `require-authentication`. Every write path is authenticated.
- **Error rule** (as chat C7): **404** when the caller cannot read the calendar or entry; **403** when they can
  read but not book or edit; **409** for a capacity conflict or a stale `version`; **422** for a booking that
  breaks the definition's rules (kind not allowed, duration, horizon, slot alignment, dangling reference).
- **Detail level:** readers without `bookRoles` get `publicDetail`; `BUSY_ONLY` strips title, description,
  references and organizer from the response – stripped on the server, not hidden in the client.
- **Capacity (RESOURCE):** in the booking transaction, lock the `calendar_lock` row, expand the overlapping
  occurrences in the new booking's span (for a series: up to the horizon), sum `quantity`, refuse with 409 and the
  conflicting occurrences. The row lock serializes bookings per resource, the same technique as chat's `seq`.
  A recurring booking without an end is refused on a resource calendar when `horizonDays` is null.

## 5a. Organization time zone

- **Owner.** The zone is a property of the organization (IANA id, e.g. `Europe/Budapest`), kept with the
  organization's other settings by platform-admin / org-admin – base-calendar owns no tenant data,
  as base-app owns none ([platform-admin extraction](../platform-admin-extraction.md)). Until that setting exists, a
  `base-calendar.default-time-zone` property (default `UTC`) stands in.
- **Port.** `OrganizationTimeZonePort.timeZoneOf(orgKey)`, adapter in the composition root; the default
  implementation returns the property.
- **Where it applies.** All-day boundaries, the default zone of new TIMED entries, horizon and slot alignment, and
  the response of `GET …/calendars/{key}` (`effectiveTimeZone`), so the widget does not ask for it separately.
- **Entries keep their own zone.** A TIMED entry still stores the zone it was entered in; changing the
  organization's zone later does not move existing entries.
- **Display.** The widget shows times in the organization's zone, labelled when it differs from the browser's
  (a remote worker sees "09:00 CET"); a per-user display zone is not planned.

## 6. Backend – `libs/java-shared/base-calendar-backend`, module `calendar`

`@ApplicationModule(displayName = "Base Calendar", allowedDependencies = {"core", "shared"})`, layout as base-chat:

```
com.processpuzzle.calendar
  definition/
    domain/              CalendarDefinition, CalendarPolicy, BookingRules, CalendarKind, CalendarDefinitionRepository
    usecases/inbound/    SaveCalendarDefinition, FindCalendarDefinition,
                         CalendarDefinitionReleaseParticipant, CalendarDefinitionImportParticipant
    adapters/inbound/    CalendarDefinitionEndpoint, mapper
  entry/
    domain/              CalendarEntry, EntryException, EntryReference, Occurrence, RecurrenceExpander,
                         CapacityCheck, AccessDecision, repositories
    usecases/inbound/    BookEntry, UpdateEntry, CancelEntry, EditOccurrence, ListOccurrences, ListMyOccurrences,
                         RespondToInvitation, EraseCalendarUser, ResolveAccess
    usecases/outbound/   CalendarReferencePort (permits by default), CalendarDirectoryPort (empty by default),
                         OrganizationTimeZonePort (property by default)
    adapters/inbound/    CalendarEntryEndpoint, OccurrenceEndpoint, IcsFeedEndpoint (phase 6)
    events/              (PlatformEvent only, see below)
  common/                CalendarProperties, problem-details mapping, @Order-ed RestControllerAdvice
```

- **Contract:** `api-contracts/.../base-calendar-api.yaml` (openapi 3.0.3), paths under
  `/organizations/{orgKey}/calendar/...`; definitions are `PUT`-by-key like chat's.
- **Facts:** booking, change and cancellation publish
  `PlatformEvent(subjectType = "calendar-entry", CREATED | UPDATED | DELETED)` through the durable registry;
  `calendar-entry` is declared as a `PlatformSubject`, so the event catalog can name it and base-state can govern
  it (D7).
- **Definitions lifecycle:** `DRAFT` / `RELEASED`, a release participant (new `DefinitionKinds` rung),
  `DefinitionReference`s to the resource entity and the entry state machine, an import participant for starters,
  `RolesReferenced` for `readRoles`, `bookRoles` and `manageRoles`.
- **Layer check:** `arch-unit-maven-plugin` with the `HexagonalLayerRules` set of base-chat, package names swapped.
- **Recurrence library:** `lib-recur` (Apache-2, small) for expansion; switch to `ical4j` only if the `.ics`
  feed of phase 6 needs more than writing VEVENTs.

## 7. Frontend – `libs/js-shared/base-calendar-frontend` (`@processpuzzle/base-calendar`)

| Layer | Contents |
| --- | --- |
| `domain/definition/` | `CalendarDefinition` types, `BaseEntityDescriptor` for authoring through a metadata entity screen |
| `domain/entry/` | Types, `CalendarService` (hand-written REST), `CalendarStore` (signals: occurrences per visible window, permissions, loading) |
| `feature/calendar/` | `pp-calendar` widget wrapping `<full-calendar>`; entry dialog (create / edit, recurrence presets, references, attendee picker) |
| `feature/attendance/` | RSVP control in the details panel; attendee list with responses |
| `feature/entry-details/` | Details panel: fields, references as links, `entryDetailWidgets` rendered through `pp-widget-host` |
| `feature/upcoming/` | `pp-calendar-upcoming` – the next N occurrences as a list, for headers, sidenavs and documents |
| `feature/definition/` | Routes for definition authoring (metadata entity screen first, own editor only if needed) |

### Widgets

| Widget | Props |
| --- | --- |
| `calendar` | `calendarKeys` (one or more, overlaid with their colors), `includeMine` (overlay the caller's invitations from all calendars), `defaultView` (`day` \| `week` \| `month` \| `list`), `views`, `firstDayOfWeek`, `slotMinTime`, `slotMaxTime`, `readOnly`, `height` |
| `calendar-upcoming` | `calendarKeys`, `includeMine`, `limit`, `days` |

Output ports: `entrySelected` (entry id), `slotSelected` (`{ start, end, allDay }`), so a document or page can
react. Registered with `provideWidget` and mirrored in the base-widget-backend seed YAML (`widget-contract.spec.ts`).

### FullCalendar integration

- Only the wrapper component imports FullCalendar; everything else talks to the store. Replacing the renderer
  later touches one folder.
- Data comes through FullCalendar's `events` function source, backed by `CalendarStore.load(window)`;
  FullCalendar never fetches by itself.
- Drag, resize and select call the store; a 409 / 422 reverts the move and shows the server's reason.
- **Theming:** FullCalendar's `--fc-*` custom properties are mapped to `--pp-*` and `--mat-sys-*` tokens in one
  stylesheet of the lib, so presets and `ThemesButton` apply.
- **i18n:** FullCalendar locale follows the active Transloco language; own texts in scope `base_calendar` with an
  `alias`.
- **Time zone:** FullCalendar's `timeZone` is set to the organization's zone (`effectiveTimeZone`, §5a), with
  `temporal-polyfill` doing the conversion.
- **Bundle size:** check the lib's production size with the four plugins before phase 3 closes; FullCalendar is
  imported only by applications that register the calendar widgets.

## 8. Working with base-chat

A booking with its own discussion is a chat **about** the entry; neither library imports the other.

- **Backend:** add `CALENDAR_ENTRY` to chat's `SubjectType`; `ChatSubjectAdapter` in the testbed composition root
  answers for it by asking base-calendar's entry use cases (exists, readable by the caller). Chat access then
  follows the booking: who can read the entry can read its chat, narrowed by the chat definition's roles.
- **Lifecycle:** a chat definition with `subject: { type: CALENDAR_ENTRY, definition: <calendarKey> }` and
  `lifecycle: AUTO_CREATE` opens the conversation when the `calendar-entry` CREATED fact arrives (chat phase 6
  listener); `ON_DEMAND` lets participants start it.
- **Frontend:** the calendar definition's `entryDetailWidgets` places `chat-panel` in the entry details panel, with
  the entry bound as its subject input. The calendar only knows it renders widget placements.
- **Convenience:** if installing two packages becomes friction, a thin re-exporting `@processpuzzle/collaboration`
  can come later; it changes nothing above.

## 9. Phases

| Phase | Scope | Done when |
| --- | --- | --- |
| 1 Scaffold | Lib pair (the 13 registration files, as base-chat phase 1), `@ApplicationModule`, `ModularityTests`, layer rules, Sonar projects | CI green; module in the Modulith log |
| 2 Core | Contract; definitions (seeded YAML); TIMED and ALL_DAY entries; occurrences endpoint; visibility, detail level and error rules; organization time zone port; `PlatformEvent` facts | API tests: anonymous reads a PUBLIC calendar, gets 404 on a ROLES one; BUSY_ONLY strips fields; 403 / 422 cases |
| 3 Widget | Store, service, `pp-calendar` with day / week / month / list, entry dialog, drag / resize, theming, i18n; testbed page with a team and a room calendar | Playwright: book, move and cancel an entry; anonymous visitor sees the public calendar |
| 3a Attendees | USER and ROLE attendees, directory port + picker, RSVP, response reset on time change, attendee read access, `my-occurrences` + `includeMine`, erasure | Playwright: organizer invites a user and a role; the invitee sees it under "mine" in a private entry and accepts; moving the entry resets the response |
| 4 Recurrence | RRULE presets, server expansion, exceptions (incl. per-occurrence RSVP), "this and following" split | Weekly series across a DST change keeps its local time; editing one occurrence leaves the rest |
| 5 Resources & definitions | RESOURCE kind, capacity check under lock, horizon; release and import participants, `RolesReferenced`, definition authoring screen, starter support | Two concurrent bookings of a capacity-1 room: exactly one succeeds; designer adds a calendar without code |
| 6 Integration | References + `CalendarReferencePort` adapters; entry state machine (approval via base-state / base-workflow); `entryDetailWidgets`; chat `CALENDAR_ENTRY` subject; `pp-calendar-upcoming`; read-only `.ics` feed per calendar | A room booking goes REQUESTED → CONFIRMED through a workflow and carries its own chat |

## 10. Settled questions and deferred work

Settled 2026-11-19:

1. **`.ics` feed** – PUBLIC calendars only (D10). Per-user secret feed tokens, which would also cover ROLES
   calendars and "my calendar", come later.
2. **Attendees** – needed; designed in §4a (D8).
3. **Reminders** – left out (D11). When a notification channel exists, reminders, invitations and change notices
   all go through it, and external e-mail attendees become possible.
4. **Time zone** – organization-wide (D9, §5a).

Open:

- **Where the organization's time-zone setting lives** – platform-admin's organization record or org-admin's
  settings; to be decided with whoever owns that schema. The property fallback unblocks the calendar meanwhile.

## 11. Phase 1 as built (2026-11-19)

The lib pair is a copy of base-chat's build wiring with the chat parts removed; the 13 registration files are
all touched (root `pom.xml` module, version property and `dependencyManagement`; `nx.json`; root `package.json`
scripts; `tsconfig.base.json`; both release scripts; the four workflows; the testbed backend's `pom.xml` and
`project.json`). Where it differs from the plan:

- **Versions start at 0.0.1** for both halves.
- **The module is not empty.** Maven's javadoc step fails a library without public classes, so phase 1 carries the
  first piece of §5a: `OrganizationTimeZonePort` (`calendar :: port`) and the `OrganizationTimeZone` use case. The
  property is `base-calendar.default-time-zone`, named like chat's `base-chat.*` keys.
- **Layer rules** are chat's, with `conversation` renamed to `entry`: `definition` must not know `entry`, and `entry`
  must not use `definition.adapters`. `HexagonalLayerRulesTest` runs them against fixtures (10 tests).
- **Frontend** has the translation scope (`base_calendar`, five languages, one key) and the `domain` → `feature`
  import-direction lint; the testbed does not copy the i18n assets yet — that comes with the widget in phase 3.
- **Not done:** the Sonar projects (`processpuzzle_base_calendar_backend` / `_frontend`) are configured in
  `sonar-project.properties` but not yet created on SonarCloud, and CI has not run.


## 12. Phase 2 as built (2026-11-19)

The contract is `api-contracts/.../base-calendar-api.yaml`: definitions (list, get, `PUT`-by-key, delete the draft),
`GET calendars/{key}`, entries (book, get, replace, cancel) and `GET occurrences`. Where the build differs from the
plan, and why:

- **Anonymous means anonymous, not "the guard let it through".** `OrganizationGuard` and `hasAnyRole` treat a caller
  without a token as permitted and as holding every role — the tenant API's open-by-default compromise. A calendar
  cannot use that, so `ResolveCalendarAccess` reads `CurrentPrincipal` directly: a caller is a *member* only with a
  valid token from this organization's realm (or as platform staff). No token, or another tenant's, is an outsider
  who sees only `PUBLIC` calendars and never books. Tests pin each cell of that matrix, and a mutation of the rule
  fails three of them.
- **Filter chain.** `GET` on `calendar/calendars/**` and `calendar/occurrences` is `permitAll`, also when
  `require-authentication` is set; every other method under `calendar/calendars/**` needs a token. The definitions
  follow `require-authentication` like every definition. Both modes are tested over HTTP.
- **Cautious defaults.** A definition that names no visibility is `AUTHENTICATED` and no detail is `BUSY_ONLY`; the
  mapper, the importer and the entity builder agree.
- **Managers can read what they manage**, even on a `ROLES` calendar whose read roles they lack; an empty manage list
  makes nobody a manager but an entry's organizer and platform staff.
- **Organizers need the booking role to edit their own entries** (`mayEdit`): losing the role stops editing.
- **Deferred inside this phase:** `RECURRING` is accepted in a definition's `allowedKinds` but entries of that kind
  are refused (422, "not supported yet"); `capacity` is stored and validated but not enforced; there is no release
  participant, so `releaseState` is not reported and nothing releases a designer's draft yet (the seed importer
  writes both rows, as chat's does); no `RolesReferenced` for the role lists.
- **Stripping is the use case's decision, applied by the mapper.** `EntryViews` computes `busy`, `mine` and
  `canEdit` once, for one entry, a list or a window; the mapper drops title, description, location, category,
  organizer and visibility from a busy view. The organizer's subject is in no DTO.
- **Errors.** 404 (`calendar.not-found`) for a missing or unreadable calendar, the same answer for both; 403 for
  book / edit / cancel without the right; 409 for a stale `version`; 422 (`calendar.booking.invalid`) for a broken
  rule, listing every violation; 400 for a bad window (empty, reversed, longer than 100 days).
- **Window query.** All-day entries are matched by local day in the organization's zone, timed ones by instant; the
  end of both is exclusive, which `CalendarEntryPersistenceTest` checks at every edge.
- **Seed:** `default-calendar-definitions/base-calendar/processpuzzle-testbed-calendar-definitions.yaml` ships a
  public events calendar, a team calendar and a room, loaded by `base-calendar.loadDefaultCalendarDefinitions`.

## 13. Phase 3a as built (attendees)

Contract: `base-calendar-api.yaml` gains `attendees` on entries, `invited`/`canRespond`, the answer endpoint
(`PUT …/entries/{id}/attendance`), `GET …/directory/users`, `GET …/my-occurrences` and `DELETE …/users/{userSub}`.
Where the build differs from §4a, and why:

- **The organizer is an invitee, accepted, from the start.** That is what lets "my calendar" and the detail rule
  treat the organizer and the invitees alike; the organizer cannot be removed from the list.
- **Only a PUT that names invitees replaces them.** A change that omits `attendees` keeps them as they are; the
  reschedule path of the widget relies on this, so a drag cannot wipe the invitations.
- **Moving the time resets every invitee's answer**, but not the organizer's, who made the move.
- **Invitations by name are checked against the directory and take the name from it.** A subject the directory does
  not know is refused (422, naming the invitation), so no invitation can carry a name nobody checked. Without a
  directory adapter nobody can be invited by name (`EmptyDirectory`).
- **The directory is exposed as `org-admin :: port`** (a named interface, so the testbed composition can adapt it).
  The adapter passes only subject and name, not e-mail or roles, and drops disabled accounts. The realm of an
  organization is its key.
- **Roles count only for members of the organization** (`CallerMembership`): a role of another realm invites nobody.
- **Erasure removes a user's invitations and anonymizes the entries they organized** (organizer becomes `erased`).
  §4a said "anonymize the attendee snapshots"; removing the invitation is the cleaner act, because a second
  anonymized row would collide with the first on the same entry's key.
- **Visibility:** an invitee sees a private entry in full, and answers without the booking right. Invitees are hidden
  in a busy view. An invitee's subject is sent only to people who may edit the entry.
- **Directory search** needs a token and at least two characters, and returns at most 20 matches.
- **Still out of 3a:** recurring entries (phase 4, §14), and the events for answers and erasure (only
  booking, change and cancellation publish facts).

## 14. Phases 4 and 5 as built (2026-11-20)

Simplified on purpose, after the decisions of 2026-11-20:

- **Recurrence (phase 4):** three presets only, daily, weekly (the weekday of the template) and monthly (its day of the
  month), ending after a count (1 to 500) or on a date, or never. The RRULE text is not stored; the series is held as
  its preset, its count or until date, and its first and last day. The server expands occurrences per query window
  and keeps the template's local time across daylight saving time. Editing changes the whole series. Not built:
  exceptions for one occurrence, "this and following", and the free-text RRULE.
- **Capacity (phase 5):** a resource calendar's capacity is the number of bookings that may overlap at one time. Each
  occurrence of a booking is checked against the bookings already in place, the entry being changed left out. The
  released calendar row is locked for the booking or change, so concurrent bookings take turns. A series is checked
  for two years of occurrences; beyond that it is not checked. Capacity counts bookings, not units, so no `quantity`
  exists. Not built: release and import of participants, `RolesReferenced`, and the definition authoring screen.
