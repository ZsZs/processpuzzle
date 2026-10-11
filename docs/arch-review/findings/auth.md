# Architecture review: auth

- **Slice code:** FAU
- **Paths in scope:** `libs\js-shared\auth`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Scoped file discovery, source/test reads and ripgrep; resolved `npm exec nx -- show project auth --json`. A network-free Node expression checked URL-prefix versus origin equality. No Angular tests, builds, lint or live identity requests executed. Deferred IDE discovery returned no usable tools.
- **Calibration:** Effort estimates are provisional; rubric scales unchanged and owner calibration unperformed.

## Summary

The library has explicit provider and bearer-interceptor extension points, and exposes signal-based session state without making every consumer know Keycloak. However, the credential destination predicate compares URL strings rather than security origins. Firebase's SDK state and the shared signals are also allowed to disagree, while a failed Keycloak initialization remains permanently cached. These are gaps in the provider abstraction and lifecycle, not a recommendation to remove the supported Firebase adapter or replace every asynchronous API with signals.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FAU-SEC-01 | Credential allow-list uses URL string prefixes | SEC | 5 | 3 platform | M | High | 7.5 |
| FAU-STA-01 | Provider session state diverges from shared signals | STA | 4 | 3 platform | M | High | 6.0 |
| FAU-OPS-01 | Transient initialization failure poisons future login | OPS | 3 | 3 platform | M | High | 4.5 |

## Findings

### FAU-SEC-01 — Credential allow-list uses URL string prefixes

- **Category:** SEC
- **Criticality:** 5
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 7.5
- **Evidence:**
  - `libs\js-shared\auth\domain\service\auth-token.interceptor.ts:57` — cross-origin authorization uses `url.startsWith(root)`.
  - `libs\js-shared\auth\domain\service\auth-token.interceptor.ts:40` — accepted destinations trigger refresh and bearer-token attachment.
  - `libs\js-shared\auth\domain\service\provide-auth-token-interceptor.ts:13` — the public contract accepts absolute API-root prefixes, including origin-only roots.
  - `libs\js-shared\auth\domain\service\auth-token.interceptor.spec.ts:85` — tests an allowed cross-origin root but not another origin sharing its textual prefix.
  - Network-free Node output for root `https://api.example` and request `https://api.example.other.test/resource`: `prefixAccepted: true`, `sameOrigin: false`.
- **Why it is debt:** The adapter promises that third-party destinations never receive a platform credential, but its matching abstraction does not establish origin identity. A configured origin-only root admits another hostname with the same prefix, and path roots similarly need segment-aware containment rather than arbitrary prefix equality. This is a verified predicate defect affecting hosts that opt into the shared interceptor, not proof of a deployed exploit or a claim that every application registers it.
- **Suggested refactoring:** Parse roots and requests as URLs, require exact normalized origin equality, and apply explicit path-boundary rules where a root restricts a subtree. Reject unsupported schemes, malformed roots and ambiguous matching rather than guessing. Add tests for prefix-sharing hostnames, user-info URLs, ports, protocol-relative requests and path siblings without making network calls.
- **Depends on / blocks:** none

### FAU-STA-01 — Provider session state diverges from shared signals

- **Category:** STA
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 6.0
- **Evidence:**
  - `libs\js-shared\auth\domain\service\auth.service.ts:8` — `currentUser` and `isAuthenticated` derive from the library-owned `_user` signal.
  - `libs\js-shared\auth\domain\service\firebase-auth.service.ts:14` — authentication reads SDK `currentUser` but never projects the restored session into `_user`.
  - `libs\js-shared\auth\domain\service\firebase-auth.service.ts:27` — logout delegates to `signOut` without clearing that signal.
  - `libs\js-shared\auth\domain\service\firebase-auth.service.ts:31` — the imperative getter instead reads SDK state and constructs a `User` even when no SDK user exists.
  - `libs\js-shared\auth\feature\auth.guard.ts:13` — after awaiting `authenticate`, routing decisions consult `isAuthenticated`, not the returned SDK result.
  - `libs\js-shared\auth\domain\service\firebase-auth.service.spec.ts:113` — logout tests verify SDK delegation, not consistency of the public signals after a prior login.
- **Why it is debt:** The provider implements multiple public answers to the same session question without one synchronization owner. A restored Firebase session can authenticate successfully while a guard still sees anonymous state; a successful logout after library login can leave signal consumers signed in, and the getter can invent a user-shaped object for an anonymous SDK session. The platform blast comes from shared guards, user widgets and host session adapters relying on the same contract; this does not imply backend authorization is bypassed.
- **Suggested refactoring:** Project the provider's authenticated/anonymous lifecycle into one canonical signal state, including restoration, successful logout and external session changes. Make the imperative getter and authentication result agree with that projection and return undefined for anonymous state. Add shared adapter-contract tests covering restore, login, logout and SDK-driven sign-out, and use the same contract to check Keycloak refresh/session loss.
- **Depends on / blocks:** none

### FAU-OPS-01 — Transient initialization failure poisons future login

- **Category:** OPS
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\auth\domain\service\keycloak-auth.service.ts:81` — `initPromise ??= this.initKeycloak()` retains a rejected promise indefinitely.
  - `libs\js-shared\auth\domain\service\keycloak-auth.service.ts:25` — login awaits that same initialization gate before initiating login.
  - `libs\js-shared\auth\domain\service\keycloak-auth.service.ts:85` — initialization includes both the SDK initialization and subsequent profile fetch, either of which can reject.
  - `libs\js-shared\auth\domain\service\provide-authentication.service.ts:76` — bootstrap catches initialization failure to keep the application available, but supplies no recovery transition.
  - `libs\js-shared\auth\domain\service\provide-authentication.service.spec.ts:100` — the outage scenario mocks `authenticate` and verifies bootstrap availability, not retry with the real initialization gate.
- **Why it is debt:** Single-flight initialization correctly avoids concurrent SDK startup, but permanent memoization turns a transient identity-provider or profile failure into an application-lifetime authentication failure. The public app can render after the caught error, yet subsequent login/authenticate calls reuse the same rejection even after service recovery. The shared provider therefore lacks an explicit recoverable failure state; full-page reload is the only reset evident in the inspected implementation.
- **Suggested refactoring:** Model initialization states and distinguish retryable profile retrieval from SDK initialization that may require a new client instance. Keep concurrent initialization coalesced, but expose a deliberate retry/reinitialize path with bounded behavior and visible failure. Test initial failure followed by recovery, concurrent retries and profile failure after successful SDK initialization rather than merely clearing the promise around an SDK that cannot initialize twice.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host registration and API-root configuration determine whether FAU-SEC-01 is reachable; no live requests were sent.
- `libs\js-shared\auth\package.json:23` declares older NgRx peer ranges than the root workspace uses. Clean published-package consumer installation was not tested, so compatibility is recorded as an unscored packaging follow-up rather than a runtime failure claim.

## Strengths worth keeping

- `libs\js-shared\auth\domain\service\provide-auth-token-interceptor.ts:18` makes credential forwarding opt-in instead of a hidden side effect of importing a library.
- `libs\js-shared\auth\domain\user\user.ts:81` copies and freezes role lists; consumers need not inspect the concrete identity client.
- `libs\js-shared\auth\domain\service\keycloak-auth.service.ts:111` resolves redirects against the document base, retaining support for applications deployed below an origin's root.
