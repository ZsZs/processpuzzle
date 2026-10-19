# E2E plan — Reports, Signatures, Calculations, Content Book, Workflows, Events

Target project: `apps/processpuzzle-testbed-e2e` (Playwright, real Spring backend).

## Decisions (2026-10-19)
- **Auth:** real Keycloak login. Test users per role in the testbed realm; a Playwright setup project
  stores one `storageState` per role.
- **CI:** e2e runs on every PR against the `ci` topology (`:9090`), not only on stage after deploy.
- **Timers:** in e2e scope. The `ci` profile shortens `base-workflow.timers.interval`; timer specs use
  their own short-duration workflows, imported and released by the spec.
- **PDF:** real jsPDF in the browser — no `PDF_RENDERER` stub. Only `window.print` is stubbed.

## Constraints
- No endpoint lists occurred events or publications (`completion-mode: delete`): event matching is
  asserted through its effects — a started instance, a calculation run, a caught event.
- Event definitions fire only once released (`/releases`).
- Seed data (handbook, ORD-1001..1004) is never reset: specs that mutate create their own data.

## Phase 0 — enablers
1. Real login:
   - The testbed frontend sends **no bearer token** today. Register `authTokenInterceptorFor([backendRoot])`
     (`libs/js-shared/auth/domain/service/provide-auth-token-interceptor.ts`) in the testbed `app.config.ts`.
     The backend already validates a token when present, even with `require-authentication: false`.
   - **No roles in the testbed realm, and none will be added.** Anyone may claim any task (it is a
     demonstration); role allocation is tested in `processpuzzle-biz`.
   - **No seeded users needed.** Testbed sign-up is open (`processpuzzle.registration.enabled: true`,
     `verify-email: false`): global setup registers a fresh, uniquely named user per run through
     `POST /registration` (org-admin-backend `RegistrationEndpoint`). Works on ci and stage alike, no secrets.
   - Login is UI-only (`directAccessGrantsEnabled: false`): a Playwright setup project goes to
     `/auth/login`, fills `#username` / `#password` on the `pp-login-form` theme and stores
     `storageState`. keycloak-js `check-sso` restores the session from the Keycloak cookie.
   - Testbed data (users included) is wiped nightly: specs create uniquely named data so they don't
     collide within a day; teardown is best-effort, not required.
2. Backend API client generalised from `support/race-sample.api.ts`: orders, state transitions,
   releases, workflow import, instances, calculation runs.
3. Per-test data creation and teardown.
4. `window.print` no-op via `addInitScript`.
5. Short timer interval for `ci`.
6. Playwright step in `build-processpuzzle-testbed-frontend.yml`.
7. Page objects per feature in `src/pages/`, on the existing `data-testid`s.

## Phase 1 — no login needed
| Spec | Scenarios |
|---|---|
| `calculation-authoring.spec.ts` | source validate/errors/save; decision table edit; evaluate with order picker → values, decisions, trace; run → listed in Calculation Runs |
| `calculation-triggers.spec.ts` | order line change → run + `total` written; confirm → frozen, later edits skipped; manual freeze |
| `report-template.spec.ts` | `order-invoice` preview `?order=`; missing-parameter state; Reports tab on Order + print; author data field / entity table |
| `content-book-reader.spec.ts` | `?doc=` deep link; unpublished page for a reader; inline tree on Content tab and small viewport; CRUD-created book |
| `workflow-modeler.spec.ts` | seeded diagram with boundary events; property panels; layer toggles |
| `workflow-authoring.spec.ts` | start events (3 types), intermediate/boundary events; release refused for unhandled transition |
| `event-catalog.spec.ts` | import; new definition fires only after release |

## Phase 2 — logged in
| Spec | Scenarios |
|---|---|
| `document-signature.spec.ts` | not-published; sign + idempotent re-sign; signature widget in report → stamp, PDF upload, link, retry; Issued Documents row (drop the list exclusion) |
| `content-book-editor.spec.ts` | own book: add new/existing (before/after/below/root), move, indent, remove, edit, publish, archive, delete, Properties |
| `workflow-dashboard.spec.ts` | order create → instance; manual start 409; claim, gated completion, complete, skip, comments; mine/team; run context; Process board |
| `workflow-events.spec.ts` | InvoiceRequested → invoicing-workflow → InvoiceIssued caught; cancel interrupts; DELIVERED → customer-feedback; instance monitoring |

## Phase 3 — cross-feature
- `order-lifecycle.spec.ts`: create order → calculated total → workflow on the dashboard → confirm
  freezes → report signed → issued document. Replaces the stub-driven living-docs scenario.
- `workflow-timers.spec.ts` (`@slow`): duration boundary timer escalates; cycle timer fire count on the
  event-instance tab.
