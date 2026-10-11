# JavaScript/TypeScript frontend architecture review

- **Date:** 2026-10-10
- **Rubric:** [RUBRIC.md](RUBRIC.md), unchanged.
- **Source baseline:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`.
- **Provenance:** HEAD advanced externally to `ef029af71eed9d01f86ffb20c544418d21b3e229`. Comparison with the baseline found no tracked frontend-source or rubric changes; the only tracked difference outside review documents was the root POM's store release property, from 1.1.3 to 1.2.0. The review changed documentation only.
- **Scope:** The 16 libraries under `libs\js-shared`, `apps\processpuzzle-testbed-frontend` and its supporting `apps\processpuzzle-testbed-e2e` project. Shared unit/E2E libraries are explicitly included as supporting slices. Java backends, Python vision-server code, private applications, live infrastructure and unrelated release tooling are excluded.
- **Stack:** The current root manifests specify Angular 22 and Nx 23; older version and scaffold descriptions in README were not treated as the current implementation.
- **Method:** Static source, dependency/public-surface, metadata, state-lifecycle and test-architecture inspection, plus read-only resolved Nx configuration where needed. No Angular tests, build, lint, browser, live identity or E2E stack execution. A network-free expression confirmed the credential URL-prefix mismatch in FAU-SEC-01.
- **Calibration:** No owner/reviewer calibration examples were supplied. Existing rubric scales are unchanged; effort estimates are provisional, not delivery commitments.
- **Relationship to the backend review:** [SUMMARY.md](SUMMARY.md) retains the Java backend synthesis. This is a separate frontend-scoped synthesis so the already delivered backend findings remain stable while being reviewed. Cross-layer remedies mentioned here require coordination; this pass does not re-score or silently revise backend findings.

## Overall assessment

The frontend has a credible metadata-driven foundation: public facade/descriptor APIs, signal stores, injected rule and context ports, host-owned composition, and independently testable transport mappers. Its principal weakness is not a lack of signals. It is that signals often hold results without the operation identity, readiness, draft revision or dataset-completeness information needed to interpret those results safely.

The highest-risk consequences are misdirected or lost edits, successful-looking failed saves, incomplete rule evaluation, and gaps between authenticated browser state and API transport. Repeated identity/context problems should be addressed through consistent small patterns and tests, not by moving every state store into one global abstraction. Explicitly supported alternate providers and demonstration modes remain valid; they should not conceal a failed configured capability.

The review records **52 findings in 18 slice reports**, consolidated into **47 work items**. Seven are separately retained as strategic debts because their criticality is at least 4 and their effort is L. The supporting-library findings also show why a green test suite alone would not settle the review: some doubles change the dependency contract, some assertions accept weaker outcomes than intended, and cleanup loses ownership on failure paths.

## Coverage

| Slice | Report | Findings |
| --- | --- | --- |
| auth | [FAU](findings/auth.md) | 3 |
| util | [FUT](findings/util.md) | 2 |
| base-entity-frontend | [FEN](findings/base-entity-frontend.md) | 6 |
| base-rule-frontend | [FRU](findings/base-rule-frontend.md) | 3 |
| base-state-frontend | [FST](findings/base-state-frontend.md) | 3 |
| base-workflow-frontend | [FWF](findings/base-workflow-frontend.md) | 5 |
| base-event-frontend | [FEV](findings/base-event-frontend.md) | 1 |
| base-app-frontend | [FAP](findings/base-app-frontend.md) | 3 |
| base-document-frontend | [FDO](findings/base-document-frontend.md) | 2 |
| base-widget-frontend | [FWI](findings/base-widget-frontend.md) | 3 |
| base-ai-frontend | [FAI](findings/base-ai-frontend.md) | 4 |
| base-starter-frontend | [FSR](findings/base-starter-frontend.md) | 2 |
| org-admin-frontend | [FOA](findings/org-admin-frontend.md) | 2 |
| design | [FDN](findings/design.md) | 2 |
| test-util | [FTU](findings/test-util.md) | 2 |
| e2e-testing | [FET](findings/e2e-testing.md) | 3 |
| processpuzzle-testbed-frontend | [FTP](findings/processpuzzle-testbed-frontend.md) | 4 |
| processpuzzle-testbed-e2e | [FTE](findings/processpuzzle-testbed-e2e.md) | 2 |

## Systemic themes

### Edit and request state need an explicit owner identity

Route reuse and overlapping work recur across preview, state/workflow diagrams, role editing, task completion, document editing, enrollment, recognition and starter selection. A late response is valid for the operation that produced it, not necessarily for the screen currently visible; a mutable `currentId` is insufficient to connect the two. The same principle applies before dispatch: a draft typed for A must not be submitted against newly selected B. Use immutable operation/session keys, obsolete-response protection and intentional draft lifecycle transitions, while distinguishing cancellation of observation from rollback of an already accepted server mutation.

Widget lifetime has the same ownership concern at a different boundary: FWI-STA-01 preserves output callbacks for an old placement when the host's shape is unchanged, and FWI-STA-02 preserves photo selection after the indexed collection changes. Same-placement value updates should retain useful widget state; different placement identity or invalidated selection should not.

### Saving is a state machine, not merely an awaited promise

FEN-API-01 resolves failed store mutations in a form that still takes its success navigation. FDO-STA-01 does not distinguish local draft revision from server acknowledgement, FST-STA-01 spans two resources without retaining the topology draft on partial success, and FTP-PER-01 deletes before replacement is confirmed. FSR-OPS-01 additionally conflates confirmed rejection with an unknown mutation outcome. Retain pending/failed drafts and target identities, expose a truthful result contract, and define when navigation, retry or reconciliation is permitted.

### Unknown, empty and successfully loaded are different states

FEN-MET-01 treats unknown rule metadata as no rules; FOA-STA-01 turns failed role reads into an editable empty permission set. FUT-OPS-01 and FUT-OPS-02 hide required-configuration or translation failures behind optional-capability fallback. The severity differs, but the root representational loss is the same. Keep deliberate absence supported while making configured-but-failed capabilities observable and recoverable; an error snackbar alone cannot repair a state model that enables an unsafe write.

### Collection completeness belongs in the query contract

The generated grid, context rule loader and workflow inbox each mistake a loaded subset for an authoritative collection in different ways. A browse page can support a paged table, but cannot silently become the complete rule set or complete task inbox. Preserve page/total/continuation semantics and screen-owned query membership; explicitly exhaustive consumers need an exhaustive operation. The operational inbox remains a separate work item because its user/task query boundary is more than iterating the generic collection's pages.

### Metadata interpretation must agree across every surface

FRU-ABS-01 and FRU-MET-01 expose a predicate evaluator that can mutate its input or accept non-boolean truthiness. FEN-MET-02 makes advisory severity blocking when attached to a field; FEN-PER-01 places business attributes in the same namespace as persistence identity. Route construction, navigation, event-kind forms, completion context and detector-class authoring each add narrower inconsistencies. Metadata-driven does not mean accepting arbitrary JSON without a semantic contract: readers, editors, validators and serializers must preserve the same meaning.

### Authentication requires both provider consistency and host composition

FAU-STA-01 concerns the provider's session projection; FTP-MET-01 concerns the host discarding roles that are now available. FTP-API-01 concerns missing bearer-transport composition, while FAU-SEC-01 concerns the safety of the optional transport when enabled. Fixing any one does not fix the others. Frontend role checks are UX, not server authorization, and this static review does not claim live endpoint exploitability.

### Test substitutes and assertions need the same contracts as production

FTU-TST-01 and FTU-TST-02 encode altered breakpoint and translation semantics in shared doubles. FET-TST-02 accepts equal-length bytes as equal content and any unsuccessful HTTP result as proof of deletion; FTE-TST-01 mistakes a locator object for a rendered element. These are not simply missing test counts: the existing oracle can accept an incorrect result. FET-TST-03 adds an unowned seed-data prerequisite. Repair the boundary assertions and exercise negative cases before using these tests as acceptance evidence for related refactorings.

### Cleanup must own resources before success is known

FET-TST-01 records a UI-created fixture only after a save/navigation boundary that can fail after persistence, and relies on that same page for cleanup. FTE-TST-02 has a separate REST ledger but can fail before reaching it or abandon later resources on the first cleanup error. Both need pending resource identities, idempotent reconciliation and cleanup that survives the failed foreground operation. They remain separate work items because the UI manager needs an independent cleanup path, while the application REST helper needs guaranteed ledger execution and failure aggregation.

### Independent publication requires an explicit dependency contract

FDN-ABS-01 and FWI-ABS-01 expose direct runtime imports that their own package manifests do not declare. Workspace resolution is not an isolated-consumer installation guarantee. Correct the respective manifests and share a packed-package consumer check, without turning the libraries into a single distribution unit. The source findings remain separate because each package has its own install contract and release acceptance; neither is fixed by changing the other.

## Consolidation and rescoring

The source reports retain their original local scores. The synthesis merges these near-duplicate work streams without deleting their distinct reproduction cases:

| Work item | Source findings | Synthesis scope and score |
| --- | --- | --- |
| FSYN-STA-01 | FAP-STA-01, FST-STA-02, FWF-STA-02, FOA-STA-02 | Identity-aware route/view sessions across app preview, state/workflow layouts and role editing. Crit 5, platform 3, L: **3.8**. Includes reactive identity changes, matching-result readiness and stale-response rejection; each original case remains a required subtask. |
| FSYN-STA-02 | FAI-STA-02, FTP-STA-01 | End-to-end recognition context across the reusable camera and its host sample. Crit 4, platform 3, L: **3.0**. Camera input generations and host race/checkpoint ownership must agree; a host-only fix cannot identify every stale camera answer. |
| FSYN-API-01 | FEN-API-02, FRU-API-01 | Explicit page versus complete-query contracts for generic entity consumers and exhaustive context-rule loading. Crit 4, platform 3, L: **3.0**. Preserve the distinct paged-grid/export and full-rule-set acceptance criteria. |
| FRU-ABS-01 | Rule evaluator and generated-form consumers | Raise blast from feature 2 to platform 3: **7.5** (5 x 3 / 2), because caller mutation crosses the shared rule-engine port used by generic forms. |
| FRU-MET-01 | Rule evaluator, dry run and generated-form consumers | Raise blast from feature 2 to platform 3: **6.0** (4 x 3 / 2), because incorrect predicate verdicts affect unrelated metadata-defined entities. |

Representative evidence for merged items:

- FSYN-STA-01: `libs\js-shared\base-app-frontend\src\lib\feature\app-preview.component.ts:58` exposes changing identity but initializes selection once; `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:106` freezes the first route identity; `libs\js-shared\base-state-frontend\src\lib\domain\modeler\data-access\diagram-definition.store.ts:54` and `libs\js-shared\base-workflow-frontend\src\lib\domain\modeler\data-access\workflow-diagram.store.ts:53` publish uncorrelated layout responses.
- FSYN-STA-02: `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:311` accepts answers without matching current inputs; `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:283` publishes observations without matching the active host context.
- FSYN-API-01: `libs\js-shared\base-entity-frontend\src\lib\base-list\base-entity-list.component.html:59` offers local paging without server continuation; `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.ts:41` discards rule-page metadata.

Not every similar symptom is a duplicate. Document autosave/session lifetime, task completion drafts, enrollment rebuilds and starter confirmation each own different mutation invariants and remain separately actionable. Rule readiness is also distinct from exhaustive rule retrieval: a complete loader can still be consulted too early, and waiting for an incomplete loader does not make its result correct.

## Ranked work items

Priority is criticality times blast weight divided by effort points (S=1, M=2, L=4, XL=8), rounded to one decimal. Blast is numeric here: 1 slice, 2 feature, 3 platform. Ties are ordered by ID, not by a claim of greater urgency. All source findings have High confidence based on static evidence; this is not a runtime reproduction label. Each source finding appears exactly once in the last column.

| Rank | Work item | Title | Crit | Blast | Effort | Priority | Source findings |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | FAU-SEC-01 | Match credential destinations by URL boundaries | 5 | 3 | M | 7.5 | FAU-SEC-01 |
| 2 | FRU-ABS-01 | Prevent rule evaluation from mutating its caller | 5 | 3 | M | 7.5 | FRU-ABS-01 |
| 3 | FAU-STA-01 | Unify provider session and shared signal state | 4 | 3 | M | 6.0 | FAU-STA-01 |
| 4 | FEN-API-01 | Give persistence failure a truthful form result | 4 | 3 | M | 6.0 | FEN-API-01 |
| 5 | FEN-MET-01 | Gate submission on known rule readiness | 4 | 3 | M | 6.0 | FEN-MET-01 |
| 6 | FRU-MET-01 | Require actual boolean rule verdicts | 4 | 3 | M | 6.0 | FRU-MET-01 |
| 7 | FTP-API-01 | Compose authenticated API transport in the host | 4 | 3 | M | 6.0 | FTP-API-01 |
| 8 | FAI-STA-01 | Bind enrollment operations to an immutable subject | 5 | 2 | M | 5.0 | FAI-STA-01 |
| 9 | FDO-STA-01 | Order autosave acknowledgements and retain dirty drafts | 5 | 2 | M | 5.0 | FDO-STA-01 |
| 10 | FOA-STA-01 | Keep failed role reads out of authoritative edits | 5 | 2 | M | 5.0 | FOA-STA-01 |
| 11 | FSR-STA-01 | Bind installation confirmation to the previewed selection | 5 | 2 | M | 5.0 | FSR-STA-01 |
| 12 | FST-STA-01 | Preserve topology drafts across partial saves | 5 | 2 | M | 5.0 | FST-STA-01 |
| 13 | FWF-STA-01 | Key completion drafts and feedback by task | 5 | 2 | M | 5.0 | FWF-STA-01 |
| 14 | FAU-OPS-01 | Recover from transient authentication initialization failure | 3 | 3 | M | 4.5 | FAU-OPS-01 |
| 15 | FET-TST-01 | Own fixture rollback before a fallible save completes | 3 | 3 | M | 4.5 | FET-TST-01 |
| 16 | FET-TST-02 | Prove artifact byte identity and authoritative deletion | 3 | 3 | M | 4.5 | FET-TST-02 |
| 17 | FTU-TST-01 | Model query-specific current breakpoint state | 3 | 3 | M | 4.5 | FTU-TST-01 |
| 18 | FTU-TST-02 | Unify translation test-helper semantics | 3 | 3 | M | 4.5 | FTU-TST-02 |
| 19 | FUT-OPS-01 | Distinguish required, absent and invalid configuration | 3 | 3 | M | 4.5 | FUT-OPS-01 |
| 20 | FUT-OPS-02 | Preserve translation failure and recovery semantics | 3 | 3 | M | 4.5 | FUT-OPS-02 |
| 21 | FWI-ABS-01 | Declare the widget package's runtime dependencies | 3 | 3 | M | 4.5 | FWI-ABS-01 |
| 22 | FWI-STA-01 | Key widget component lifetime by placement identity | 3 | 3 | M | 4.5 | FWI-STA-01 |
| 23 | FDN-STA-01 | Match the design route by segments, not prefixes | 2 | 2 | S | 4.0 | FDN-STA-01 |
| 24 | FEN-STA-01 | Invalidate facade instances when descriptors reset | 4 | 2 | M | 4.0 | FEN-STA-01 |
| 25 | FEN-PER-01 | Separate business attributes from persistence identity | 5 | 3 | L | 3.8 | FEN-PER-01 |
| 26 | FSYN-STA-01 | Establish identity-aware route/view sessions | 5 | 3 | L | 3.8 | FAP-STA-01, FST-STA-02, FWF-STA-02, FOA-STA-02 |
| 27 | FAI-MET-01 | Derive detector choices from capabilities | 3 | 2 | M | 3.0 | FAI-MET-01 |
| 28 | FAP-MET-01 | Render nested widget routes | 3 | 2 | M | 3.0 | FAP-MET-01 |
| 29 | FAP-MET-02 | Align navigation with parameterized route definitions | 3 | 2 | M | 3.0 | FAP-MET-02 |
| 30 | FDN-ABS-01 | Declare the design package's direct framework peers | 3 | 2 | M | 3.0 | FDN-ABS-01 |
| 31 | FEN-MET-02 | Keep advisory field verdicts nonblocking | 3 | 2 | M | 3.0 | FEN-MET-02 |
| 32 | FET-TST-03 | Give row-dependent LIST checks an explicit fixture owner | 2 | 3 | M | 3.0 | FET-TST-03 |
| 33 | FSR-OPS-01 | Distinguish rejected, applied and unknown install outcomes | 3 | 2 | M | 3.0 | FSR-OPS-01 |
| 34 | FST-STA-03 | Reconcile governed-machine cache after authoring writes | 3 | 2 | M | 3.0 | FST-STA-03 |
| 35 | FSYN-API-01 | Separate paged browsing from exhaustive retrieval | 4 | 3 | L | 3.0 | FEN-API-02, FRU-API-01 |
| 36 | FSYN-STA-02 | Correlate camera answers and host recognition context | 4 | 3 | L | 3.0 | FAI-STA-02, FTP-STA-01 |
| 37 | FTE-TST-01 | Assert rendered shell structure in smoke checks | 3 | 1 | S | 3.0 | FTE-TST-01 |
| 38 | FTP-MET-01 | Project available roles into the workflow session | 3 | 2 | M | 3.0 | FTP-MET-01 |
| 39 | FDO-STA-02 | Close document/locale editing sessions explicitly | 5 | 2 | L | 2.5 | FDO-STA-02 |
| 40 | FTP-PER-01 | Preserve an observation until correction succeeds | 5 | 1 | M | 2.5 | FTP-PER-01 |
| 41 | FWF-ABS-01 | Query a task inbox rather than one instance page | 4 | 2 | L | 2.0 | FWF-ABS-01 |
| 42 | FWF-MET-01 | Represent typed task-completion context | 4 | 2 | L | 2.0 | FWF-MET-01 |
| 43 | FWI-STA-02 | Reconcile photo selection with replacement images | 2 | 2 | M | 2.0 | FWI-STA-02 |
| 44 | FAI-OPS-01 | Release resources acquired after cancellation | 3 | 1 | M | 1.5 | FAI-OPS-01 |
| 45 | FEV-MET-01 | Reflect event variants in form applicability and validity | 3 | 1 | M | 1.5 | FEV-MET-01 |
| 46 | FTE-TST-02 | Guarantee REST-fixture cleanup after partial setup | 3 | 1 | M | 1.5 | FTE-TST-02 |
| 47 | FWF-STA-03 | Preserve unsaved diagram geometry during display changes | 3 | 1 | M | 1.5 | FWF-STA-03 |

## Strategic debts

These meet the rubric's criticality >= 4 and effort >= L rule after synthesis. Their lower numeric priority is an effort effect, not permission to defer data integrity until after feature expansion.

| Work item | Crit | Effort | Priority | Required design or acceptance boundary |
| --- | --- | --- | --- | --- |
| FEN-PER-01 | 5 | L | 3.8 | Separate envelope identity/version from arbitrary business payload names; preserve round-trip compatibility across generic forms, mappers and transports. |
| FSYN-STA-01 | 5 | L | 3.8 | Establish route/view session identity across four consumers; retain all four stale-response or reused-route cases rather than introducing an untested global store. |
| FSYN-API-01 | 4 | L | 3.0 | Agree on browse-page versus exhaustive-query semantics, including continuation, totals, rule loading and export. |
| FSYN-STA-02 | 4 | L | 3.0 | Carry recognition generation/context through camera output and host reads/writes; prove late answers cannot be reassigned to a new race/checkpoint. |
| FDO-STA-02 | 5 | L | 2.5 | Define flush/cancel/reconcile behavior for document and locale changes, after ordered acknowledgements preserve pending edits. |
| FWF-ABS-01 | 4 | L | 2.0 | Coordinate a server-backed task-inbox query with membership, role/user filtering, paging and frontend projections. |
| FWF-MET-01 | 4 | L | 2.0 | Coordinate metadata, typed completion forms and the API input contract without losing task-keyed draft ownership. |

## Proposed refactoring sequence

This is a proposal only; no fixes have been implemented. Begin owner calibration on a small task, a feature-local state task and one strategic item before treating the effort sizes as a delivery plan. Numerical rank is not a topological order: prerequisites below take precedence.

| Stream | Dependency-aware order | Parallelism and acceptance |
| --- | --- | --- |
| Authentication and host composition | FAU-SEC-01 before FTP-API-01; FAU-STA-01 establishes the session lifecycle for FTP-MET-01. FAU-OPS-01 can proceed alongside provider-state work. | The host role mapping can be edited independently, but accept it against a consistent restored/login/logout session. Verify both allowed API destinations and credential exclusion elsewhere before registering transport broadly. |
| Rule correctness and metadata readiness | FRU-ABS-01 and FRU-MET-01; the FRU-API-01 portion of FSYN-API-01 before accepting FEN-MET-01's full-rule-readiness guarantee. FEN-MET-02 can proceed in parallel. | Test mutation attempts, non-boolean verdicts, multiple rule pages, loading/failure and field-level advisory severity. The paged-grid/export portion of FSYN-API-01 need not block the first rule-loader subtask. |
| Persistence result and envelope boundaries | FEN-API-01 first for truthful form outcomes; design FEN-PER-01 as its own compatibility project. | The envelope project can run alongside local state repairs, but must preserve business fields named `id` or `version` and optimistic-concurrency metadata. |
| Document editing | FDO-STA-01 before FDO-STA-02. | Parallel with state/workflow work. Accept overlapping edits, failed acknowledgements and document/locale changes with a pending save, not only successful reloads. |
| State authoring and reused views | FST-STA-01 before FST-STA-03. Deliver FSYN-STA-01 in slice-sized subtasks; coordinate its FOA-STA-02 subtask with FOA-STA-01. | Invalidate machine caches only after topology success, not layout-only partial success. App preview and state/workflow response correlation can proceed in parallel with the shared acceptance pattern agreed first. |
| Workflow operation and modeler | FWF-STA-01 before FWF-MET-01; the FWF-STA-02 portion of FSYN-STA-01 before FWF-STA-03. FWF-ABS-01 is a separate query-contract stream. | Inbox-query design can proceed alongside task draft ownership and typed completion. Do not accept a complete-looking inbox merely because every item in one generic page renders. |
| Enrollment and recognition | FAI-STA-01 independently; FSYN-STA-02 before FTP-PER-01, particularly its FTP-STA-01 host-context subtask. | FAI-OPS-01 resource cancellation and FAI-MET-01 capability metadata can run in parallel. Accept correction failure without losing the original observation and late camera responses without context reassignment. |
| Starter installation | FSR-STA-01 before FSR-OPS-01. | Independent of the editing streams. Preserve which selection was previewed and distinguish known rejection from an unknown accepted-server outcome. |
| Supporting test infrastructure | FTU-TST-01, FTU-TST-02, FET-TST-02 and FTE-TST-01 can start independently. FET-TST-01 and FTE-TST-02 repair separate cleanup paths; FET-TST-03 should reuse the repaired shared lifecycle if its fixture hook adopts that manager. | Run these alongside product fixes, before relying on the affected tests as acceptance gates. Include false/negative dependency cases, wrong same-length bytes, service errors after deletion and partially completed setup. |
| Independent authoring and widget follow-ups | FEN-STA-01, FAP-MET-01, FAP-MET-02, FDN-STA-01, FEV-MET-01, FWI-STA-01 and FWI-STA-02 have no hard inter-item prerequisites. | Assign by owning slice; widget identity and photo-selection reconciliation need separate tests because live prop updates must work without remounting. |
| Configuration and publication | FUT-OPS-01 and FUT-OPS-02 can proceed independently; FDN-ABS-01 and FWI-ABS-01 can share an isolated packed-consumer check. | Preserve deliberate optional-source behavior while exposing required-source failure. Validate each published package's own declared runtime dependencies rather than workspace hoisting. |

## Strengths and boundaries to preserve

- Feature libraries expose useful public APIs and host-provided context/engine ports. Repair their contracts without making every feature import application internals.
- The widget registry composes injector scopes, the shared host centralizes input/output binding, and widget contract tests compare component interfaces with metadata. Keep these boundaries rather than adding aggregator-specific renderers.
- Dedicated mappers and generated descriptors give metadata changes a common path across forms and transports. Fix readiness, identity and round-trip semantics at these seams.
- Document read-back and artifact/relationship E2E flows already test more than local UI echoes; improve their oracles and failure cleanup rather than discarding the reusable suite machinery.
- Signal stores, ComponentStore and RxJS are not findings by themselves. Ownership, completeness, result contracts and lifecycle behavior are the meaningful criteria.

## Review limitations and follow-through

This is a static architectural assessment, not a claim that all risks have occurred in production or that all unlisted code is defect-free. No live browser/provider/backend stack, test suite, clean published-package consumer or concurrency reproduction was run. Effort estimates require owner calibration; backend policy, API changes and release migration need their respective owners.

Each implementation should first turn its cited failure case into an acceptance check, including the relevant negative or interleaving case. Preserve explicit anonymous/demo modes, optional adapters and illustrative living-documentation fixtures, while distinguishing them from proof of a configured production capability. Keep the Java backend reports separate and stable until a deliberate cross-layer planning pass reconciles the two inventories.
