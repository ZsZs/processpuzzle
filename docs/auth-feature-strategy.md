# ProcessPuzzle Auth Feature — Strategy Note

## Status
Login/Logout implemented. `base-workflow-backend` synchronises roles from domain model to Keycloak. This note covers the design for the next phase: registration, a ProcessPuzzle-branded login experience, admin-driven role assignment, and default-administrator bootstrapping.

## Guiding decision: no Direct Access Grants
Credential validation stays inside Keycloak via the Authorization Code + PKCE flow. Resource Owner Password Credentials (Direct Grant) was considered and rejected — it would expose password handling in the frontend/backend, forecloses MFA and social login, and is discouraged by current OIDC practice. This constraint shapes every choice below.

## Login: reuse the existing Angular form, inside Keycloak
The existing Angular pop-up login form is packaged as an **Angular Element (Web Component)** — a standalone build target in the Nx monorepo producing a single JS/CSS bundle (e.g. `<pp-login-form>`).

- A custom Keycloak theme (`processpuzzle`) includes that bundle and renders `<pp-login-form>` in `login.ftl` in place of Keycloak's default markup.
- The component's emitted username/password values feed the real `<form action="${url.loginAction}">` — required so Keycloak's CSRF/session handling and the PKCE code flow work unmodified.
- The frontend opens the Keycloak authorization URL in a **popup window** (rather than a full-page redirect) to preserve the pop-up interaction users already know. A small callback route captures the returned auth code, `postMessage`s it to the opener, and closes itself; the main app performs the standard PKCE code exchange.

Net effect: users see the exact component already built and styled; Keycloak's default UI is never shown; the flow underneath is unchanged Authorization Code + PKCE.

## Registration: bypass the theme, go through the backend
Only Testbed needs self-registration (Custom/Biz are invite-only — see below). Rather than embedding a component in `register.ftl`, the existing Angular registration form posts directly to the ProcessPuzzle backend, which creates the account via the **Keycloak Admin REST API** (using a backend-held service account). This is the same mechanism used for org-admin invites, so no additional theme work or Keycloak-side flow is needed for registration.

## Custom/Biz: invite-only via org-admin-frontend/backend
OrgAdmins never get direct Keycloak access. All invite, role-assignment, and group-assignment operations for their organisation go through ProcessPuzzle's own **org-admin-frontend/backend** module (a bounded context inside the Custom app, following the existing hexagonal/Spring Modulith conventions), which is the sole caller of the Keycloak Admin API for these operations.

- **Invite**: backend creates the Keycloak user, adds them to the Keycloak Organization, then calls `execute-actions-email` (`UPDATE_PASSWORD`, `VERIFY_EMAIL`) so Keycloak sends a themed email with a set-password link — no custom invite-token system needed.
- **Roles vs. groups**: only **roles** round-trip to Keycloak (they need to appear in the JWT for `OrganizationGuard`/authorization checks). **Groups** are proposed as a pure PP-domain concept (workflow/team assignment, e.g. `RoleUse` in the SPEM model) with no Keycloak representation, to keep the service account's Keycloak footprint minimal.

## Admin-driven role assignment
Reverses the existing sync direction (today: Keycloak → domain). A `KeycloakRoleAssignmentPort` outbound adapter, backed by the same service account, pushes role changes made in the PP Admin UI to Keycloak's Admin REST API (`role-mappings` endpoints), with a domain event keeping local state consistent.

## Default administrator per organisation
Not a runtime feature — a deploy-time bootstrap. **Implemented** in `bootstrap-platform-admin-client.sh` (the `keycloak-init` service, re-run on every start; `FIRST_ADMIN_<STACK>_*` variables, see `tools/docker/keycloak/README.md`) rather than a `KeycloakBootstrapService`; the realms themselves still come from the baked-in imports. It provisions a first admin user from environment-supplied credentials. This applies uniformly to **every** app — Testbed and Admin included, not just Custom — so the platform's own realms are bootstrapped the same way customer orgs are.

## Backend topology (reconfirmed)
The 4-separate-backend strategy stays as-is: Testbed, Admin, Biz, and Custom each remain independent deployables, all assembled from the same shared feature libraries via the Nx monorepo (so there's no code duplication to maintain — only operational overhead). This is a security/blast-radius decision, not a code-organisation one:
- **Admin** stays isolated for its VPN-restricted, cross-tenant administrative boundary.
- **Testbed** stays isolated from **Custom** to contain the blast radius of its daily data reset.
- **Biz** stays its own deployable, and now assembles `platform-admin` alongside `platform-subscription` rather than calling Admin over REST for every operation. The footprint is no longer small, which was the wrong thing to optimise: a "tiny" Biz owned no data at all, so with Admin not deployed the sign-up funnel served nothing. Being *independently deployable* is what this bullet was actually for, and embedding the feature is what delivers it.
  - **Assembling Admin's feature is not the same as publishing Admin's surface.** Biz maps neither `/platform/**` nor `/organizations/**` on its port — the controllers are beans for an in-process dispatcher only — and the staff organization-management controller is not wired into even that. So the blast-radius reasoning above still holds: the VPN-restricted boundary is about which *paths* an openly reachable service answers, not about which jars are on its classpath.
  - What it does share is the `processpuzzle_admin` database, which is the one deliberate exception to §3's isolated-database-per-app rule in `processpuzzle-architecture-summary.md`. One tenant registry is the point: a signup taken on the public site has to be the row the staff console sees.

## Open items
- Confirm whether "groups" in the org-admin module should ever need to affect Keycloak-issued token claims (would require reconsidering the Keycloak-groups-free approach above).
- Angular Elements extraction, theme scaffolding, and the registration/Admin-API endpoint are the concrete next build tasks.
