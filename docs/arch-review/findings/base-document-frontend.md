# Architecture review: base-document-frontend

- **Slice code:** FDO
- **Paths in scope:** `libs\js-shared\base-document-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static IDE source/directory inspection, scoped file reads and ripgrep. Inspected manifests/exports, document mapping and properties calls, route/content loading, signal-store ownership, Tiptap/embedded-widget lifetimes and HTTP/component tests. Tests, build, lint and Nx boundary checks were not executed; no `project.json` read or Nx configuration query needed. Slice peers confirm Angular 22; Nx 23 is the supplied review baseline.
- **Calibration:** Owner/reviewer calibration was not performed. Effort estimates are provisional; rubric scales and effort points are unchanged.

## Summary

Separating document properties from locale-specific content is a sound boundary, and the service deliberately prevents properties saves from resubmitting stale blocks. The editor also uses a component-local signal store rather than mixing continuous editing into the generic CRUD store. Two substantial state-machine gaps remain: autosave acknowledgements/errors do not preserve the newest local draft, and document/locale changes do not delimit a complete editing session. These findings concern concrete overwrite and mis-targeting paths, not the choice to use RxJS debouncing or the absence of future binding features.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FDO-STA-01 | Autosave has no ordered acknowledgement or recoverable dirty state | STA | 5 | 2 feature | M | High | 5.0 |
| FDO-STA-02 | Document and locale changes do not close the previous editing session | STA | 5 | 2 feature | L | High | 2.5 |

## Findings

### FDO-STA-01 — Autosave has no ordered acknowledgement or recoverable dirty state

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:95` — a text edit patches local state immediately, then a per-block debounce invokes `void this.persistBlock(...)` without awaiting or catching the write.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:183` — a full block replacement is sent and its response unconditionally patches the current local block at line 189.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:112` — widget property saves also use the same replacement path without a per-block in-flight queue.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.service.ts:39` — each call is an independent PUT promise, with no sequence/precondition parameter supplied by the editor.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-editor.component.ts:85` — visible request state is append-specific; the error UI at lines 57–58 is not connected to text autosaves.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.spec.ts:68` — the inspected replacement test exercises one immediate write, not two outstanding writes or a rejected text debounce.
- **Why it is debt:** Debouncing limits request frequency but does not order outstanding requests or acknowledgements. If a newer edit is made while an older PUT is pending, the older response can replace that newer local block; two completed debounce windows can also leave competing full replacements in flight. A rejected text save has no caught error, retryable pending revision or dirty state in this store, so the editor can continue showing text whose persistence failed. The blast includes the feature's persisted authored content, not just a loading indicator.
- **Suggested refactoring:** Model pending, in-flight, acknowledged and failed revisions explicitly per block, retaining the latest local draft independently of server acknowledgements. Serialize writes per target or otherwise enforce revision-aware ordering, and apply an acknowledgement only to the revision it represents. Expose saving/dirty/error state with retry and a flush operation; add deferred-response tests for old acknowledgements, overlapping edits and failed autosaves.
- **Depends on / blocks:** blocks FDO-STA-02

### FDO-STA-02 — Document and locale changes do not close the previous editing session

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.5
- **Evidence:**
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-content-tab.component.ts:80` — the input-driven content tab selects/fetches only on initialization; the editor binding at line 31 nevertheless reads the current `entityId`.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-editor.component.ts:101` — any input change reinitializes the same store with the new document/locale and supplied blocks.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:58` — initialization changes the mutable target and blocks but does not drain/cancel debounce subjects, invalidate pending responses or clear output values.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:103` — the delayed callback captures a block id but reads the mutable document/locale later in `persistBlock` at lines 184–188.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:147` — a pending reorder replaces the whole current block list after awaiting, without checking whether the session changed.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-editor.component.ts:28` — text children are reused by block id.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-text-block.component.ts:80` — Tiptap content/editability and the update callback are initialized once; there is no input-change reconciliation for a reused text child.
  - `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-editor.component.spec.ts:131` — the reuse test swaps distinct widget block ids, so it does not exercise pending text saves or equal-id blocks across locales.
- **Why it is debt:** Identity is spread across a one-shot tab load, mutable store fields and independently long-lived editor instances rather than one session boundary. Changing the tab id can pair the new write target with previously loaded blocks; even when a host supplies correct replacement blocks, an old debounce can send old content to the new target if its block id also exists there. Late responses can mutate the replacement session, and a same-id text child can retain the old locale's Tiptap buffer. These are concrete content-integrity risks in the supported same-instance reuse model.
- **Suggested refactoring:** Introduce an immutable document/locale session key and generation shared by loading, editors and queued writes. Reload reactively for target changes, settle or explicitly discard outstanding dirty work before switching, and ignore responses from obsolete generations. Recreate or explicitly reset Tiptap editors on session changes even when block ids match, and release subscriptions/outputs on session close and destruction. Cover equal-id locale changes, tab-id reuse, delayed loads/PUTs/reorders, and navigation during the debounce window.
- **Depends on / blocks:** depends on FDO-STA-01

## Out-of-scope observations

- Backend concurrency controls, generated OpenAPI definitions, shared CRUD store internals, HTTP interceptors and the host router/reuse policy were not inspected. The report does not claim server authorization or concurrency behavior; the local stale-response and mutable-target paths are sufficient evidence.
- `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.store.ts:65` explicitly defers host-context input binding. That unfinished capability alone is unscored.
- Published-package installation was not tested. The review inspected peer declarations and exports but concentrates scoring on the demonstrated editor lifecycle risks.

## Strengths worth keeping

- `libs\js-shared\base-document-frontend\src\lib\domain\base-document.service.ts:26` routes generic updates through a properties-only payload; `libs\js-shared\base-document-frontend\src\lib\domain\base-document.service.spec.ts:63` explicitly checks that content is not sent.
- `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-editor.component.ts:25` gives each editor its own content store, separate from root generic CRUD state.
- `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-content.service.ts:29` includes document and locale in every block resource path instead of keeping the current locale in the root HTTP service.
- `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\document-editor.component.ts:134` guards concurrent append clicks and surfaces append failures explicitly; the autosave path should gain comparable guarantees.
- `libs\js-shared\base-document-frontend\src\lib\feature\document-editor\widget-embed-node.ts:160` destroys the reactive effect and mounted Angular component when a ProseMirror node view is destroyed.
