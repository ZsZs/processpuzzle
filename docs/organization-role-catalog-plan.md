# Organization Role Catalog Plan

Status: **approved, phase 0 implemented (uncommitted)** · Drafted 2026-10-26 · Owner: ZsZs

Gives the organization administrator ownership of the organization's **role catalog** — the list a
document's reader / editor / publisher fields, a navigation item's `roles` and a user's role
assignment all pick from — and closes the authorization gap that would otherwise let any member write
to it.

## Why now

Document authoring was lately tied to a *selectable* role list (`REALM_ROLE_SUGGESTIONS` →
`GET /organizations/{orgKey}/role-catalog`). A list that is picked from has to be managed somewhere,
and today nothing in the administration area manages it:

| Fact | Where |
| --- | --- |
| The admin area has **no role screen**, by design: roles are "the realm's list, read to populate a checkbox set". | `org-admin-frontend/src/lib/org-admin.routes.ts` (route comment), `UserDirectoryPort` ("cannot create a role") |
| The org-admin **can assign / revoke** — but only via a user: *Users → user → Roles* tab, `PUT /admin/users/{id}/roles`. | `role-assignment.component.ts`, `role-assignment-tab.ts` |
| On the **custom stack** (`keycloak.admin.organization-realm`) the catalog is exactly the organization's **Keycloak Organization groups**, and the only writer of those is base-workflow's projection of `RoleDefinition`s — i.e. the designer's *Workflows → Roles* screen. | `KeycloakOrganizationRoleDirectoryAdapter`, `KeycloakOrganizationUserDirectoryAdapter.findRoles` |
| A role **typed** into a document role field is stored but, on the custom stack, **never created**: `UserDirectoryConfiguration` wires `NoOpRoleProvisioningPort` in organization-realm mode. The picker still renders it "dashed — about to be created". Nobody can ever be granted it, so a document gated by it is readable by no one. | `UserDirectoryConfiguration.java:54`, `tags.component.ts:65`, `tag-suggestions.ts:10` |
| `/organizations/{orgKey}/admin/**` is callable by **any member**: `TenantRealmResolver.resolve` calls `requireDesign`, and both deployed policies implement that as membership only. Known gap, recorded in `auth-feature-strategy.md`. | `TenantRealmResolver.java:63`, `CustomerOrganizationAccessPolicy.requireDesign` |

So: the designer defines roles as a side-effect of modelling processes, the administrator can only
hand them out, and nobody can define a role that is not a process role or clean up one that was typed.

## Decisions

| # | Question | Decision |
| --- | --- | --- |
| 1 | Who owns the catalog? | **The org-admin.** Roles projected from the designer's `RoleDefinition`s and the platform's `org-admin` / `org-member` appear in the catalog **read-only**; everything else the administrator creates, describes and deletes. |
| 2 | How is a role's origin known? | A **Keycloak attribute** on the role / group, `processpuzzle.origin` ∈ `workflow` \| `admin` \| `reference`, written by whoever creates it. org-admin cannot ask base-workflow (sibling libraries), and an attribute travels with the object. A role without the attribute (pre-existing) counts as `admin`. |
| 3 | Can a role be renamed? | **No.** Name is the key everywhere — documents, nav items, tokens, `RoleDefinition.id` — and a rename would silently detach all of them. Only the description is editable. Same rule `RoleDefinition.id` already follows. |
| 4 | When may a role be deleted? | Only an `admin`-origin role, and only when **no user holds it** and **no content names it** — 409 listing what still refers to it. Platform roles never; `workflow` roles only through the designer (base-workflow's `deleteRole` already does that). |
| 5 | How does org-admin know what content names a role? | A **core port, contributed to by each feature**: `RoleReferenceContributor { Map<String, List<RoleReference>> referencedRoles(orgKey) }` in `processpuzzle-core`, implemented by base-document (documents, content books), base-app (nav items, route definitions) and base-workflow (role definitions). org-admin collects all beans — the same "many implementations, collect" shape as `ReleaseParticipant`. No new inter-library dependency. |
| 6 | Who may call `/admin/**`? | A new policy method, **`requireAdministration(orgKey)`** — membership *and* the `org-admin` authority (or staff). `requireDesign` stays what it is for the designer. Reads a member legitimately makes — `/role-catalog`, `/me/roles` — move to `requireAccess`. |
| 7 | Should the pickers stay free text? | **Restricted when a catalog is bound**: a `tagsRestricted` flag on the attribute descriptor, set by the document, content-book, nav-item and route-definition role fields. Unknown text is refused instead of shown dashed. If the suggestion source *fails*, the control falls back to free text (the author must not be blocked by an outage); if it answers *empty*, the field offers nothing — correct, there is nothing to gate by. |
| 8 | Is `RolesReferenced` → `EnsureReferencedRoles` still needed? | **Kept for realm-per-tenant** (Testbed), where it does create. Once the pickers are restricted it becomes a safety net for the API path only. Its port is folded into the new catalog port (decision 9), which also makes it write `origin=reference`. |
| 9 | Which port writes the catalog? | A new **`RoleCatalogPort`** in org-admin: `create`, `updateDescription`, `delete`, `findRoles` (with origin), `countHolders`. It replaces `RoleProvisioningPort` (`ensureRoles` becomes `create` with origin `reference`, 409 tolerated). `UserDirectoryPort` keeps its promise of never minting a role from an *assignment*. Two adapters, realm and organization, picked by `UserDirectoryConfiguration` as today. |
| 10 | Where does the screen live? | In `org-admin-frontend`, as a second entry of the administration branch: `/{orgKey}/admin/organization-role`, a base-entity list + details like Users. The host's sidenav lists it next to Users. |

## Gaps this plan closes

- Any member can invite, delete and re-role users (`requireDesign` = membership).
- A role that is not a process role cannot be defined anywhere.
- A role's description cannot be corrected without the designer.
- A typed role on the custom stack is a dead reference that locks documents.
- Nothing reports which content a role still gates before it disappears.

## Out of scope

- **Per-organization `org-admin`.** In the shared realm `org-admin` is a realm role, so a person who
  administers organization A and is a plain member of B would, after decision 6, also pass
  `requireAdministration("B")`. Fixing that means making `org-admin` an organization group (or a Keycloak
  organization role) and changing the seed; recorded here as the next step after this plan, not part of it.
- A "Members" tab on a role (who holds it, with links to the users). Nice, costs one Keycloak call per
  role; left for after the catalog exists.
- Workflow `RoleUse` / teams — PP-domain only, no Keycloak representation (`auth-feature-strategy.md`).

## Phases

Each phase is one core PR, releasable alone; phase 4 is the biz PR that consumes them.

### Phase 0 — Authorization (core + biz) — *first, and independently valuable*

core:
- `OrganizationAccessPolicy.requireAdministration(String orgKey)`, default deny; `OrganizationGuard` passthrough;
  `PermitAllOrganizationAccessPolicy` permits.
- `TenantRealmResolver`: `resolve(orgKey)` → `requireAdministration`; new `resolveForMember(orgKey)` →
  `requireAccess`, used by `FindRoleCatalog`, `FindMyRoles`, `ReplaceMyRoles` (`SelfServiceRoleSupport`),
  `FindUserName`. Everything else in `usecases/inbound` keeps `resolve`.
- Testbed `JwtOrganizationAccessPolicy`: `requireAdministration` = its existing membership rule **plus**
  `org-admin` or `platform-admin` authority, keeping its unauthenticated-permit compromise.
- `StaffOrganizationAccessPolicy`: staff.
- Tests: `TenantRealmResolverTest` (403 for a member without `org-admin` on an admin use case; 200 on
  `role-catalog`), `OrgAdminEndpointsTest` happy paths unchanged.
- `auth-feature-strategy.md`: strike the "known gap" sentence.

biz:
- `CustomerOrganizationAccessPolicy.requireAdministration`: `organizations.isMemberOf(orgKey) &&
  principal.authorities().contains("org-admin")`. Unit test for the three cases.
- Verify on stage that `zszs` / `zszs-2`'s first users carry `org-admin` (the seed grants it) **before**
  bumping, or the Users screen goes 403 for them.

### Phase 1 — Catalog write API (core, `org-admin-backend`, `api-contracts`, `processpuzzle-core`)

contract (`org-admin-api.yaml`):
- `OrganizationRole` gains `origin` (`platform` \| `workflow` \| `admin` \| `reference`) and keeps
  `platformManaged` for existing clients.
- `POST /organizations/{orgKey}/admin/roles` (`NewOrganizationRole { name, description }`) → 201; 400 for a
  reserved or malformed name (`[a-z0-9][a-z0-9-_]*`, same shape `RoleDefinition.id` accepts); 409 when it exists.
- `PUT /organizations/{orgKey}/admin/roles/{roleName}` (`{ description }`) → 200; 403 when origin ≠ `admin`.
- `DELETE /organizations/{orgKey}/admin/roles/{roleName}` → 204; 403 when origin ≠ `admin`; 409
  `RoleInUse { holders: n, references: [{ feature, kind, id, title }] }`.

core (`processpuzzle-core`):
- `RoleReferenceContributor` + `RoleReference(feature, kind, id, title)` in `com.processpuzzle.core.tenancy`.
- Implementations: base-document (`readerRoles` / `editorRoles` / `publisherRoles` of documents and content
  books — one query each), base-app (nav items, route definitions), base-workflow (`RoleDefinition` ids —
  which also makes a `workflow` role that lost its attribute undeletable).

org-admin:
- `RoleCatalogPort` (decision 9) + `KeycloakRealmRoleCatalogAdapter` / `KeycloakOrganizationRoleCatalogAdapter`;
  both read and write the `processpuzzle.origin` attribute; `countHolders` = `GET /roles/{name}/users?max=1`
  resp. `GET /groups/{id}/members?max=1` (existence, not a count — enough for the guard; the UI shows "in use").
- Use cases `CreateOrganizationRole`, `UpdateOrganizationRole`, `DeleteOrganizationRole`
  (reserved check reused from `EnsureReferencedRoles.isReserved`, moved to a shared `RoleNames` helper);
  `FindOrganizationRoles` returns origin. `EnsureReferencedRoles` switches to the new port.
- `OrgAdminApiExceptionHandler`: `RoleInUseException` → 409 with the body above; `ReservedRoleNameException` → 400.
- base-workflow: `KeycloakOrganizationRoleDirectoryAdapter` / `KeycloakRoleDirectoryAdapter` write
  `processpuzzle.origin=workflow` on upsert (attribute on the representation; one line each).
- Tests: `OrganizationRoleUseCasesTest` (create / reserved / duplicate / update non-admin → 403 / delete with
  holder → 409 / delete with reference → 409 / delete clean → 204), adapter tests against the existing
  Keycloak stubs, `RoleCatalogEndpointTest` unchanged.

### Phase 2 — Administration screen (core, `org-admin-frontend`)

- Domain: `OrganizationRole` gets `origin`; `OrganizationRoleService` gains `create / updateDescription / delete`;
  new `OrganizationRoleStore` + `OrganizationRoleFacade` + `createOrganizationRoleDescriptor()` — columns
  *Name · Description · Origin*, `name` as the link; `description` the only editable field; `isHeading` on name.
  Entity name `Organization Role` → segment `organization-role` (the same `snakeCaseName` constraint the route
  comment documents for users).
- Route added to `ORG_ADMIN_ROUTES`: `{ path: 'organization-role', title: 'ProcessPuzzle - Roles', data: { icon:
  'badge', menuTitle: 'admin.roles', entityName }, component: OrganizationRoleContainerComponent, children:
  baseEntityRoutes() }`; `ORG_ADMIN_FACADE_PROVIDERS` / `ORG_ADMIN_ENTITY_FACADES` extended.
- `OrganizationRoleContainerComponent`: list + "New role" dialog (name, description) modelled on
  `InviteUserDialog`; Edit / Delete disabled unless `origin === 'admin'`; a `workflow` row shows "Managed in
  the designer" with a link to `/{orgKey}/design/…/workflow-role-definition/{name}` (the host supplies the
  prefix — `DESIGN_ROUTE_PREFIX` already exists for this); delete 409 renders the holders / references list.
- Rewrite the route comment ("There is no route for roles of their own") — it will be false.
- i18n: `organization_role.*` keys in every language file the library ships.
- Same library, same release — **fix the two arch-review debts** in `RoleAssignmentComponent`
  (`FOA-STA-01` failed read becomes an editable empty set; `FOA-STA-02` user id frozen at first snapshot):
  explicit `loading | loaded | failed` state, Save only from `loaded`, `paramMap` subscription with
  `switchMap`. Small, and this is the one time the screen is being touched.
- Specs: container (list, create, delete-409 rendering, buttons disabled by origin), facade, service, routes spec.

### Phase 3 — Restricted pickers (core, `base-entity-frontend` + consumers)

- `BaseEntityAttrDescriptor.tagsRestricted?: boolean`; `TagsComponent`: when set and the source answered
  (even empty), `add()` accepts only known values and `pick()` is the way in; the dashed "will be created"
  style disappears; a short hint "Roles are managed under Administration → Roles" under the field.
- Set it on `readerRoles / editorRoles / publisherRoles` (`base-document.descriptors.ts`,
  `content-book.descriptors.ts`) and `roles` (`nav-item.descriptors.ts`, `route-definition.descriptors.ts`).
- Testbed keeps `EnsureReferencedRoles` for API callers; nothing else changes there.
- Specs for `TagsComponent` (restricted + loaded, restricted + failed source, unrestricted unchanged).

### Phase 4 — biz: consume and verify

- Bump `processpuzzle-core.version` in `pom.xml` and the `external/processpuzzle-core` submodule in one PR
  (backend from GitHub Packages, frontend through the submodule's `tsconfig` paths).
- `CustomerOrganizationAccessPolicy` from phase 0 lands with it if not merged earlier.
- Custom app: the `/{orgKey}/admin` route carries `menuTitle: 'admin.users'` on the parent; check
  `navigableRoutes` renders both children (Users, Roles) or move the menu titles to the children.
  Translation keys `admin.roles` in the custom app's bundles.
- e2e (`processpuzzle-biz-e2e`, the Keycloak support helpers already exist): as `zszs`'s admin — open
  *Administration → Roles* → create `accounting` → assign it to a second user → delete refused (409 shows
  the holder) → unassign → delete → gone from the catalog; as a non-admin member — `/admin/organization-user`
  answers 403.
- Stage: re-seed is not needed; confirm the first users hold `org-admin` (phase 0 note), then the manual pass
  above on `custom.stage.processpuzzle.de/zszs/admin`.

## Risks

- **Phase 0 can lock out an organization** whose first user was seeded without `org-admin`. Check stage
  before bumping; the seed job's grant is the recovery path.
- **Keycloak attribute support**: realm roles and groups both carry `attributes`, but the Organization
  *groups* endpoint (`/organizations/{id}/groups`, 26.6+) must round-trip them — verify against the stage
  Keycloak before building on decision 2; fallback is a group description prefix, which is uglier.
- **Reference contributors run N queries per delete** (one per feature). Acceptable for an admin action;
  not to be reused for the list screen.
- **Multi-organization accounts** (decision 6, out of scope) — a user with `org-admin` administers every
  organization they are a member of. Already true for the designer today (`requireDesign` = membership),
  so no regression, but worth a sentence in the release notes.

## Resolved questions (2026-10-26, owner)

1. **Content-reference guard: in the first cut.** Decision 5 stands as written; `RoleReferenceContributor` ships in phase 1, and delete returns 409 for holders *and* references.
2. **Restricted pickers everywhere.** Decision 7 stands; Testbed loses type-to-create, and `EnsureReferencedRoles` remains only as an API-path safety net.
3. **Role-assignment debts (FOA-STA-01/02) are fixed in phase 2**, in the same release as the Roles screen.
