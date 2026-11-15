# Shared Data ("Commons") Concept for ProcessPuzzle Business Starters

Status: concept, agreed in discussion on 2026-11-15. Next step: manifest schema.

## 1. Problem

Some custom applications, such as SailRC, must share part of their data across all subscribers:

- Each yacht club subscribes to the SailRC custom app individually.
- Clubs will extend and change the features of the original business starter over time. This is the core selling point of ProcessPuzzle.
- Entities such as Sailor, Boat, Boat Class, Yacht Club and Race must nevertheless be available to all SailRC instances.
- Sharing is limited to subscribers of the same starter. It never crosses domains.

Making the data completely open was rejected. Personal data (Sailor) requires consent and control (GDPR), and open writes would destroy data quality.

## 2. Goals

- A generic mechanism: any business starter can declare a common part (for example `sailrc-common`).
- Subscriptions are connected to the common automatically.
- The platform provides only the mechanism. It does not own the shared data or run governance.
- Clubs can still extend and customise their own application freely.

## 3. Core Concepts

### 3.1 Common (data space)

- Every business starter may declare one common, for example `sailrc-common`.
- The common is an organisation of type `COMMON`, stored in the same pooled database under its own `orgKey`.
- It has an administrator organisation: a real organisation (for example a federation) later, the platform owner initially. Handing over administration means reassigning a role, not migrating data.
- Subscribers join the data space automatically at provisioning.

### 3.2 Separate data ownership from schema ownership

- Shared entities have a platform-governed core definition: a small, versioned set of attributes that tenants cannot change.
- Clubs add local extension attributes in their own org, keyed by the global record's ID. Example: a global Sailor core record plus a club overlay (membership status, internal notes).
- Core definition changes ship as a definition release through the migration-set mechanism.

### 3.3 Sharing policy per shared entity

Write policy (who may write):

| Policy | Meaning |
|---|---|
| `COMMON_ADMIN` | Only the common's administrators |
| `OWNER_ORG` | Only the org that owns the record |
| `SELF` | Only the person the record describes |

Read visibility:

| Visibility | Meaning |
|---|---|
| `PUBLIC` | Readable by all members of the data space |
| `OWNER_ONLY` / `PRIVATE` | Not shared |

### 3.4 References, not copies

- Tenant data references shared records by `(commonOrgKey, id)`.
- There is no synchronisation problem.
- If query performance requires it, local denormalised projections can be added later via Spring Modulith events.

## 4. Application to SailRC

| Entity | Write policy | Visibility | Notes |
|---|---|---|---|
| Boat Class | `COMMON_ADMIN` | Public | Curated reference data; changes via proposal and approval workflow |
| Yacht Club | `OWNER_ORG` | Public | Each club maintains its own record |
| Race | `OWNER_ORG` | Public once published | Drafts stay private |
| Boat | `OWNER_ORG` or `SELF` | Public | Global registry (class, sail number, photos); owner claims it |
| Sailor | `SELF` | Consent-based, minimal public core | Open design question, see section 5 |

## 5. Sailor: self-defined (open question)

`SELF` means a record owned by a person, not an organisation, so the platform needs a user-owned record linked to a Keycloak user.

Candidate flow:

1. A club creates a minimal stub (name plus an identifier), visible only to that club.
2. The sailor later claims it via an invite link and decides what becomes public.
3. This avoids duplicate identities and keeps GDPR consent with the person.
4. Guests without a Keycloak account can exist as stubs.

Decision: keep `SELF` in the write-policy model from the start; design the exact Sailor flow later.

## 6. Provisioning

- The starter manifest declares the common and its shared entities.
- The customer seed ([customer-seed](customer-seed)) gets an extra step: when an org subscribes to a starter that declares a common, it is added to that data space.
- The common org itself is created once, from the Admin app.

## 7. Enforcement (technical outline)

- Same pooled database (`PROCESSPUZZLE_CUSTOM`) and the existing `@IdClass` pattern with `orgKey` in the primary key.
- The Postgres row-level security policy is extended: a record is readable if its visibility is shared, and writable only per its write policy (owner org, common admin, or the owning user).
- The `OrganizationGuard` is extended so access to common records goes through a defined path (for example a dedicated endpoint or scope) instead of the tenant `:orgKey` check.
- Cross-org JSONB/RSQL queries cannot simply join. References are resolved in the application layer or via a read-through cache.

## 8. Governance (outside the platform)

Handled by the common's administrator organisation, not by ProcessPuzzle:

- Approving new Boat Classes and Yacht Clubs.
- Detecting and merging duplicate sailors and boats, supported by external identifiers (World Sailing ID, class plus sail number).
- Dispute handling for shared records.

## 9. Decisions Taken

1. Generic mechanism: every starter may declare a common part.
2. Subscriptions are connected to the common automatically.
3. The common is assigned a real organisation (or the platform owner as administrator initially).
4. Yacht clubs define races, which are publicly available.
5. Sailor is self-defined; the exact flow is deferred.
6. ProcessPuzzle provides the mechanism only, no data ownership or governance.
7. World-wide scope with `country` as an attribute, not per-country commons.
8. Completely open data is rejected.

## 10. Open Questions

- Exact Sailor claim and consent flow.
- Whether clubs need to work offline or independently if the common is unreachable (for example at a race on the water), which would require local caching of referenced records.
- How the common's administrator role transfers to a federation later.
- Manifest schema: the `common:` block and the per-entity sharing policy (next step).
