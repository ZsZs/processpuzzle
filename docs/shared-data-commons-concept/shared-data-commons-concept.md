# Shared Data ("Commons") for Business Starters — Concept

Status: concept v2, 2026-11-16. Rewrites v1 (2026-11-15) after review against the code and the
[business starters design](../business-starters/business-starters-design.md),
[definition release](../definition-release.md) and
[definition migrations](../definition-migration-concept/definition-migrations-concept.md).

## 1. Problem

Organizations subscribed to the same business starter extend and change their application freely — that is
the core selling point. Some of their data must nevertheless be one data set for all of them: curated
reference data (asset types), the participating organizations' public profiles, publicly announced events,
a registry of assets that move between organizations.

- Sharing is limited to the subscribers of one starter. It never crosses starters.
- Completely open data is rejected: open writes destroy data quality, and personal data needs consent (GDPR).

## 2. Goals and non-goals

- **Generic.** Any starter may declare a common; a starter without one is unaffected.
- **Part of the platform.** A common is created and maintained by ProcessPuzzle, the starter's author. It is
  never deleted and needs no subscription.
- **Defined once.** Shared entity types exist in exactly one place, the common. Subscribers cannot change or
  extend them; they create their own entity types that **refer to** the common ones.
- **Simple until needed.** No row-level security (see §8), no new runtime, no data copies.
- **Not in scope:** governance of the shared data (who approves what, duplicate merging, disputes). The
  platform enforces who may write; deciding what is right is the common's administrator's business.

## 3. Core concepts

### 3.1 The common is an organization

A common is an ordinary organization with a reserved key, one per starter: `<starterId>-common`.

- It lives in the same pooled custom database and is served by the same `processpuzzle-custom-backend` as
  every subscriber. It has no backend, realm or database of its own.
- Its members are ProcessPuzzle staff. They curate its data with the generated CRUD screens of the shared
  types; a curation app of its own is not planned.
- It is never deleted. The tenant data cleaner refuses a common's key.

This is the global entity type already planned in `EntityObjectScope` (reserved key `_global`, never
implemented), scoped to one starter. The commons replace `_global`; the javadoc is updated with step 1.

### 3.2 Membership is derived, not stored

An organization is a member of a common exactly when it has the common's starter installed —
`starter_installed (org_key, starter_id)`, which the seed already writes. No membership table, no
provisioning step to forget. `processpuzzle-core` declares a `DataSpaceDirectory` port
(`commonOf(orgKey)`, `isCommon(orgKey)`); `base-starter-backend` implements it, so base-entity depends on no
starter code.

### 3.3 Shared entity types

A shared entity type is an entity definition of the common, released in the common like any definition.

- **Read-only for members.** A member's definition lookup (`EntityDefinitionLookupPort`) also answers the
  common's *released* shared types. The member's designer lists them, marked as common and read-only; a
  member's release resolves references to them without checking them (they are not the member's to release).
- **Reserved codes.** A member may not define an entity type with the code of one of its common's types;
  saving, importing and releasing refuse it.
- **Sharing policy on the definition**, not in the manifest — the runtime reads it from the same place as
  everything else, and the designer can show it:

```yaml
sharing:
  write: COMMON_ADMIN        # COMMON_ADMIN | OWNER_ORG | SELF (reserved, §7)
  visibility: PUBLIC         # PUBLIC | OWNER_ONLY
  visibleWhen:               # optional: public only in these states of the type's state machine
    state: [ PUBLISHED, COMPLETED ]
  from: event                # optional: owner and visibility follow the record this REFERENCE
                             # attribute points to (here: a booking follows its event)
identifiers:                 # natural keys, unique among non-retired records
  - [ assetType, serialNumber ]
```

### 3.4 Records: where they live and who owns them

- Every record of a shared type is stored under the **common's** org key. `EntityObjectScope.storageOrgKey()`
  — the seam already built for this — answers the common's key for a shared type.
- New columns on `entity_object`, all nullable: `owner_org_key` (the member that created it, for
  `OWNER_ORG`), `owner_subject` (reserved for `SELF`), `retired_at` / `retired_by` (§3.6).

| Policy | Read | Write |
|---|---|---|
| `COMMON_ADMIN` + `PUBLIC` | every member | the common's members (ProcessPuzzle staff) |
| `OWNER_ORG` + `PUBLIC` | every member | the owner org; the common's members too |
| `OWNER_ORG` + `OWNER_ONLY` | the owner org | the owner org |
| `visibleWhen` | others see the record only in the listed states | unchanged |
| `from: <attribute>` | as the referenced record | as the referenced record |

**Records that belong to another record.** Much shared data is part of something one organization runs: an
event is organized by one member, but its bookings and outcomes should be as public as the event. Such types
are shared types too, with `from: <attribute>`: owner and visibility are those of the referenced record —
one level, to a type of the same common, not chained. Only the event's owner writes its bookings, and they
become public when the event does. Embedded components need nothing: they are stored inside their parent and
share its record. Members of other organizations contributing to someone else's event (booking themselves)
is contribution, deferred (§7).

All of it is decided in one place, `EntityObjectScope` and the access policy behind `OrganizationGuard`, and
nowhere else.

### 3.5 References, not copies

- A member's entity refers to a shared record with the existing `REFERENCE` value kind (`FOREIGN_KEY` /
  `LOOKUP` control) and `linkedEntityType: <shared type code>`. Object ids are UUIDs, unique across
  organizations, so the stored value stays a plain id. **No new attribute type.**
- **References point into the common, never out of it.** A shared type may refer to shared types of the same
  common only; a member's type may refer to its own types and to its common's. A reference from a common
  record to a member's record would not resolve for anyone else, so saving, importing and releasing a shared
  type refuse it. Data that others must see therefore has to be a shared type (`from:` above); mirroring a
  member's records into the common was considered and rejected — two copies, synchronization, and the
  member's customized shape would need mapping back onto the common one.
- Resolving the reference goes through the scope, which reads it from the common. Pickers search the
  common; lists resolve labels in one batch.
- What a member would have added to a shared type goes into **its own** type that refers to it (a member's
  `Member Asset Profile` with a reference to the shared `Asset`). There are no overlays and no `extends`.
- Cross-organization queries do not join: a filter on a referenced record's attributes is not offered (it is
  not offered within an organization either — `AttributePathResolver` does not traverse references).

### 3.6 Nothing is hard deleted in a common

- Deleting a record of a shared type **retires** it: `retired_at` is set, the record keeps resolving by id
  (shown as retired), and searches and pickers leave it out. The common's members can reinstate it.
- No API hard-deletes a common's records. A hard delete is a platform maintenance operation, done by hand.
- When a member organization is deleted, its own data goes as today. Shared records it owns stay; with their
  owner gone only the common's members can write them.
- The delete guard's reference check (`EntityObjectRepository.existsAnyReferenceTo`) is today a text scan
  across all organizations. It is limited to the organization and its common.

## 4. Who changes what

| Change | By | How |
|---|---|---|
| Shared type definitions | ProcessPuzzle | the starter repository, released as a new common bundle version, upgraded into the common (§5.3) |
| Shared records (`COMMON_ADMIN`) | ProcessPuzzle staff | the custom application, logged in to the common |
| Shared records (`OWNER_ORG`) | the owning member | its own application, through its own `orgKey` path |
| Member definitions | the member's designer | as today; may refer to shared types, never change them |

The starter repository is the source of truth for the common's definitions. The common's designer is not used
to edit them; a change made there would be overwritten by the next upgrade.

## 5. Starters: the common bundle, replace and upgrade

### 5.1 Two parts per starter

A starter with a common is split into a **common part** and a **customisable part**. They differ in target
(one organization, forever, versus every subscriber), in who applies an upgrade (ProcessPuzzle, at once,
versus each organization's administrator, when it suits them) and in whether anyone may change them (no one
versus the subscriber's designer). Each part is therefore its own bundle with its own version, packaged and
rolled out separately. They stay in one folder because they are authored and reviewed together, and the
customisable part is written against the common part:

```
business-starters/<starter-id>/
  manifest.yaml            # tenant bundle; names the common it needs
  entities/ ...            # member-side definitions, may reference shared types
  common/
    manifest.yaml          # common bundle
    entities/ states/ rules/ apps/ ...
    migrations/
```

The tenant manifest gains one block (`manifestVersion: 2`):

```yaml
common:
  id: <starter-id>-common
  minVersion: 1.2.0        # the common version the tenant definitions were written against
```

The common manifest is a starter manifest with `kind: common` and no `common:` block. Examples:
`sailrc-starter.manifest.yaml` in this folder. `BundleReader` stops ignoring unknown top-level fields for
`manifestVersion: 2`, so a misspelt block fails packaging instead of being dropped.

### 5.2 Replace and upgrade are two operations

| | Replace (exists) | Upgrade (new) |
|---|---|---|
| When | the organization holds no objects and no workflow instances | any time, to a newer version of the installed starter |
| Definitions | all removed, the bundle's imported | per definition, by provenance (below) |
| Data | untouched (there is none) | migrated by the bundle's migration steps |
| Result | released at once on sign-up, drafts in the designer | drafts plus pending migration steps, then a release |

Upgrade, per definition, using the provenance the importer already records (`starter_definition_provenance`):

- in the bundle, untouched in the org (fingerprint equals the recorded hash) → replaced by the new content;
- in the bundle, customized in the org → kept, reported as a conflict (v1: the org's version wins);
- new in the bundle → added;
- dropped from the bundle → removed if untouched, kept if customized.

Migration steps come with the bundle: `migrations/<from>-to-<to>.yaml`, in the operations of the migration
catalog (v1: rename attribute, fill attribute, map values). They become the release's pending steps, so the
existing release machinery checks, dry-runs and applies them in one transaction. An upgrade goes one version
at a time; a skipped version is a chain of upgrades. The manifest's `migration: null` placeholder is replaced
by this.

### 5.3 Upgrading a common

The same upgrade, into the common, released by ProcessPuzzle. One check is added to the common's release:
the member organizations' definitions that refer to a shared type or attribute — references of attributes,
rules, calculations, documents and workflow artifacts. Removing or renaming something a member refers to is
an error naming the members; everything else goes through. Member data never needs migrating for a common
release: members store only ids.

A tenant install or upgrade whose `common.minVersion` is not yet in the common is refused.

**Consequence of deferred member upgrades:** at any time members run different versions of the customisable
part against the one current common. A common release must therefore stay compatible with every customisable
version still installed — which is what the reference check above enforces, since it reads the members'
definitions as they are, whatever version they came from.

### 5.4 Rollout

Publishing a version (CI puts it in the bucket, §4 of the starter design) does not yet offer it to anyone.
ProcessPuzzle decides when to **roll out**, from a rollout screen in platform-admin (biz repository):

1. The screen lists the starters, their published versions and, per version, how many organizations run it.
2. Rolling out a version of the **common part** upgrades the common at once: Admin calls the custom
   backend's internal upgrade endpoint for `<starterId>-common`, which upgrades and releases it (§5.3). A
   refused release (members' references broken) stops the rollout and is shown with the plan.
3. Rolling out a version of the **customisable part** makes it the *offered* version of the starter: the
   custom backend stores it per starter, new sign-ups install it, and existing subscribers are told an
   upgrade is available. It can be rolled out only once its `common.minVersion` is rolled out.
4. Each organization's administrator applies the upgrade from the designer when it suits them (§5.2): drafts
   and pending migration steps, then a release.

Rollout records (who rolled out which version when, and the outcome) live in Admin's database. The custom
backend only knows the offered version per starter and what each organization has installed.

### 5.5 Creating a common

The first rollout of a starter's common part creates the common: Admin creates the Keycloak organization,
seeds it without a starter, then replace-installs the common bundle. Later rollouts upgrade it (§5.4). Installing the starter into a subscriber is refused while its common does not exist; the SeedJob
records the failure as for any failed install.

## 6. Security model

- One custom backend serves all customers. Isolation is the `{orgKey}` path checked against the caller's
  organization by `OrganizationGuard`, plus the org filter in every query.
- Commons add exactly one rule: a request for organization *A* may read and — per policy — write records
  stored under *A*'s common, because the scope says so. The path is still *A*'s; a member never addresses the
  common's key directly. Requests addressed to the common's key need membership of the common, as for any
  organization.

## 7. Deferred

- **`SELF` and consent.** Records owned by a person (`owner_subject`), claim by invitation, per-attribute
  visibility, erasure across members. Needs its own GDPR design; the column and the enum value are reserved.
- **Contribution by proposal.** Members proposing changes to `COMMON_ADMIN` data through a workflow in the
  common.
- **Conflict resolution in upgrades** beyond "the org's version wins".
- **Local projections** of shared records via events, if lookups become slow; **offline** use, which is an
  application concern.
- **A dedicated backend for a large organization.** The pooled database and the org-key scope do not
  prevent it, but commons then span backends; considered when an organization needs it.
- **Transferring administration** of a common to an outside organization (a federation): adding its users to
  the common — a membership change, no data moves.

## 8. Database enforcement

No row-level security. Tenancy is enforced in application code today and commons follow that. Flyway — and with
it RLS as a second line of defence, if wanted — comes when the platform is made production ready. The new
columns are added by Hibernate, like every column so far.

## 9. Implementation plan

Each step is a core PR, then library releases, then a biz bump where noted.

1. **Shared reads (base-entity, core).** `DataSpaceDirectory` port; `storageOrgKey()` answers the common for
   shared types; the definition lookup answers the common's released shared types, read-only; reserved codes; references only into the common (§3.5);
   the `sharing` block on entity definitions (`COMMON_ADMIN` + `PUBLIC` only); member release resolves
   references into the common. Testbed: a demo common with domain-neutral types.
2. **References in the frontend.** Picker and label resolution against shared types; common types read-only
   in the member's designer.
3. **Retire instead of delete.** `retired_at` / `retired_by`, delete becomes retire for shared types,
   reinstate, searches and pickers skip retired; the delete guard's reference check limited to org + common;
   the tenant data cleaner refuses a common.
   *Backend done:* `DELETE` on a shared type sets `retiredAt`/`retiredBy`; `POST …/{id}/reinstate`;
   `includeRetired` on the list (the common's own members only). Frontend: a retired notice and Reinstate on the form, and a "Show retired records" toggle on the list of a shared type (retired rows shown struck through; the backend honours it for a record's owner and the common's own members).
4. **Common bundle and manifest v2 (base-starter).** `kind: common`, the tenant `common:` block, strict
   fields for v2, packager support, refusing an install whose common is missing or too old. Biz: create a
   common from Admin.
   *Core done:* `manifestVersion: 2` (`kind`, `common:`, `migrations`, strict top-level fields), `CommonRules` at import
   (a common bundle only into its own organization, which it registers as the starter's data space; a tenant bundle
   never into a common, and not while its common is missing or older than `minVersion`), commons left out of the
   subscriber catalog, and the packager reading a starter's `common/` folder as a second bundle. Admin's "create a
   common" (biz) is the remaining part.
5. **Owner writes.** `owner_org_key`, `OWNER_ORG`, `OWNER_ONLY`, `visibleWhen`, `from:`; natural-key uniqueness
   from `identifiers`.
   *Slice 1 done:* `owner_org_key`, `OWNER_ORG` writes (the owner and the common's members change and retire a record; a member
   creating one becomes its owner) and `OWNER_ONLY` visibility, in the scope, the instance use cases and the embedded
   collections (which did not check the shared-type write rule before). Also done: `identifiers` (natural keys, checked in the
   application on create, update and reinstate; the database does not enforce them yet, §8); `visibleWhen` (non-owners see a
   record only in the listed states; the core port `RecordStates`, implemented by base-state, says where the state is
   kept; without it only the owner sees the record); and `from:` (a type whose records follow another: owner and
   visibility are the followed record's, only its owner or the common adds one, and the followed attribute cannot
   change; a type that follows takes no `write`/`visibility`). Step 5 is complete.
6. **Starter upgrade.** Provenance-based upgrade, bundle migration steps as pending steps, one version at a
   time; the same operation for tenants and commons; the offered version per starter; "upgrade available"
   and Apply in the designer for the org administrator; the internal upgrade endpoint for a common.
   Biz: the rollout screen in platform-admin (§5.4) and its rollout records.
   *Done so far:* the participant SPI for definition-by-definition upgrades (entity, rule, state, chat) and `UpgradeStarter` /
   `UpgradePlanner` in base-starter (replace untouched, keep customized, add new, remove dropped; drafts; one version at a time;
   kinds that cannot be upgraded yet are left alone and named). Also done: the offered version (`OfferStarterVersion`), the installed-starters listing's `upgradeAvailable`, the member upgrade
   endpoint, and `UpgradeCommon` — upgrade and release of a common in one transaction, so a release refused by the members' references
   check (step 7) rolls the upgrade back. The participants of every kind now upgrade definition by definition (calculation, module, app, translation and
   asset included; the app-side kinds write drafts only and an app is never removed by an upgrade). Bundle migration steps are done too: `migrations/<from>-to-<to>.yaml` (`migration: {from, to, steps: [{kind, key,
   operation, subject, parameters}]}`, the release machinery's own steps) is read when the bundle is, checked by the packager, and an
   upgrade queues the file for exactly its step of versions as pending steps (`MigrationStepQueue`, implemented by base-release),
   in the same transaction as the drafts; the release runs them. Open: the
   application-side endpoints that expose `OfferStarterVersion` and `UpgradeCommon` to the platform's rollout.
7. **Common release check** against members' references (§5.3).
   *Done (type level):* releasing a common refuses to take away, or rename, a type that a member's definitions still refer to
   (`ReleasePlanner`, `ReleaseParticipant.removedReferableKeys`, `DataSpaceDirectory.membersOf`), naming the members and what
   refers. It reads the members' *drafts' references*, so it covers every kind that reports references; references to a single
   attribute of a shared type are not checked yet.

Steps 1–3 make a curated common usable; 4 makes it installable from a real starter; 5–7 can follow in any
order once a starter needs them.

## 10. Decisions on the v2 open questions (2026-11-16)

- **Upgrade conflicts:** the organization's customized version wins; the upgrade reports it. Choosing per
  definition is deferred.
- **Rolling back a rollout** only withdraws the offered version. Organizations that applied it keep it.
- **Data of one member that all must see** is a shared type owned by that member (`OWNER_ORG`, `from:`), not
  a mirror of the member's records (§3.5).
- **Scope of the work:** generic only. SailRC is the driving example; nothing in the platform is built for it.
