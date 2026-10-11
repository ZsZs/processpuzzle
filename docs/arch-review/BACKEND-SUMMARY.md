# Java backend architecture review

**Frontend review:** See [JavaScript/TypeScript frontend architecture review](FRONTEND-SUMMARY.md) for the separate frontend findings, ranking and refactoring sequence. The backend synthesis below is unchanged.

- **Date:** 2026-10-10
- **Rubric:** [RUBRIC.md](RUBRIC.md), unchanged.
- **Scope:** All 14 libraries under `libs\java-shared` and `apps\processpuzzle-testbed-backend`. Frontends, the Python vision server, private repositories and live infrastructure are excluded.
- **Baseline:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`.
- **Provenance:** HEAD advanced externally during review to `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`. Git comparison confirmed no tracked differences in the 15 source slices or rubric; the root POM alone changed the widget release property from 1.1.0 to 1.2.0. Findings therefore retain the baseline source references. No application code, configuration or rubric changes were made by this review.
- **Method:** Static source, configuration, dependency, contract and test-architecture inspection; resolved Nx project discovery where needed. Tests and Modulith declarations were inspected, not executed. This is not a runtime concurrency reproduction, deployment security audit or exhaustive defect inventory.
- **Calibration:** No owner-scored calibration examples were supplied. Existing rubric scales were used unchanged; all effort estimates remain provisional, not delivery commitments.

## Overall assessment

The Java backend has a credible modular structure: explicit Modulith declarations, feature-owned ports, composition-root adapters, metadata interpreters and useful transaction/event seams. Several features are substantially more mature than the top-level README's scaffold labels suggest. The major debts are not indiscriminate module cycles; they are mismatched consistency boundaries inside otherwise well-separated modules: persisted metadata versus executable caches, client edits versus database versions, committed rows versus external effects, and mutable definitions versus running instances.

The review records **43 findings in 15 slice reports**, consolidated below into **38 work items** after merging four sets of near-duplicates. Prioritize the critical authorization and rule-consistency findings before expanding on these mechanisms. Do not let the numerical ordering hide large data-integrity projects: the separate strategic-debt list explicitly retains high-criticality work with larger effort.

## Coverage and source reports

| Slice | Report | Findings |
| --- | --- | --- |
| api-contracts | [API](findings/api-contracts.md) | 2 |
| processpuzzle-core | [PCR](findings/processpuzzle-core.md) | 2 |
| processpuzzle-store | [PST](findings/processpuzzle-store.md) | 3 |
| base-entity-backend | [BEN](findings/base-entity-backend.md) | 4 |
| base-rule-backend | [BRU](findings/base-rule-backend.md) | 2 |
| base-state-backend | [BST](findings/base-state-backend.md) | 4 |
| base-workflow-backend | [BWF](findings/base-workflow-backend.md) | 5 |
| base-event-backend | [BEV](findings/base-event-backend.md) | 2 |
| base-app-backend | [BAP](findings/base-app-backend.md) | 3 |
| base-document-backend | [BDO](findings/base-document-backend.md) | 3 |
| base-widget-backend | [BWI](findings/base-widget-backend.md) | 3 |
| base-ai-backend | [BAI](findings/base-ai-backend.md) | 3 |
| base-starter-backend | [BSR](findings/base-starter-backend.md) | 2 |
| org-admin-backend | [OAD](findings/org-admin-backend.md) | 3 |
| processpuzzle-testbed-backend | [PTB](findings/processpuzzle-testbed-backend.md) | 2 |

Every source finding retains category, criticality, blast radius, effort, confidence, score, file/line evidence, rationale, suggested refactoring and dependencies. Strengths and unscored scope limitations remain in each report.

## Systemic themes

### Authorization is not consistently owned by the use-case boundary

PCR-SEC-01, BWI-SEC-01 and BDO-SEC-01 describe different gaps, not duplicate reports of one bug: implicit permit-all policy selection, missing widget enforcement, and alternate document paths bypassing parts of their own policy. OAD-SEC-01 exposes a further distinction between owning an organization membership and owning the global account shared by memberships. The root problem is that a path organization, a policy bean and a host security filter are sometimes treated as interchangeable guarantees. Keep host authentication separate, but make each use case's authorization obligations and permissive-mode selection explicit; these findings do not establish internet exploitability.

### Database transactions do not automatically cover caches or external work

BRU-PER-01 publishes executable rule changes before commit, while BRU-MET-01 does not invalidate per-thread compiled functions after a metadata revision. Cleanup in AI, store and the host bridge can acknowledge failure or discard its retry identity. Workflow tools, state actions and organization invitations similarly cross remote side-effect boundaries without an adequate durable outcome contract. An after-commit callback protects rollback ordering, not delivery after a crash; an outbox protects delivery only if handlers preserve failure and replay semantics. Apply a common design principle, not one generic framework imposed on unrelated features.

### Optimistic locking is being used at the wrong granularity

App/state authoring needs the revision the client edited, not just a row freshly loaded by the write request. Workflow MESSAGE delivery needs occurrence ownership across potential recipient rows; parallel joins need a serialized progression decision, not only serialized terminality checks. AI jobs require attempt-aware transitions, starter replacement needs exclusion shared with instance writers, and remote role replacement needs an explicit concurrency contract. The common mistake is protecting an individual write while leaving the business invariant's read/decision/write interval unprotected.

### Mutable metadata lacks a complete evolution policy

An editable definition is not necessarily safe to apply to existing data or in-flight work. BEN-MET-02, BST-MET-01, BWF-MET-01, BEV-MET-01, BAI-MET-01 and BDO-MET-01 expose different instances: populated entity schemas, occupied states, running workflows, live subscriptions, queued recognition jobs and published document bindings. These remain separate work items because rejection, migration and immutable snapshots are not interchangeable remedies. Agree on revision/retirement principles across feature boundaries, then implement the smallest correct lifecycle for each.

### Architectural guarantees need tests at their actual boundary

Modulith verification tests already protect many feature boundaries; their presence is a strength, not a claim that they passed during this review. However, map-backed import participants cannot prove rollback, direct listener calls cannot prove durable second-hop publication, and single-threaded mocked repositories cannot prove concurrency semantics. Contract generation also disables specification validation while duplicated canonical schemas have no parity gate. Extend the existing tests with targeted transaction, replay, compatibility and contention fixtures rather than replacing fast unit tests with an indiscriminate full-system suite.

### Internal dependency direction and storage ownership still leak

BAP-LAY-01 and BDO-LAY-01 place inbound mappers and generated models inside application orchestration; BWI-ABS-01 exposes persistence through the advertised module surface. Storage presents a streaming port while buffering full objects, and original/thumbnail deletion and nested artifact discovery disagree about logical ownership. These are concrete abstraction costs, not a demand to remove every Spring or JPA annotation. Preserve pragmatic module internals where they do not compromise an intended boundary.

## Consolidation and rescoring

Slice scores remain unchanged as the local review record. The ranked list uses the following explicit synthesis adjustments; scores are rounded to one decimal using the rubric's effort points.

| Work item | Source findings / affected slices | Consolidated decision |
| --- | --- | --- |
| SYN-OPS-01 | BAI-OPS-01, PST-OPS-01, PTB-OPS-01; AI, store, testbed host | One durable cleanup/delivery program, with three distinct implementation boundaries retained. Crit 4, platform 3, L (4 points): **3.0**. Cross-slice recovery, persistence and outage tests justify L rather than summing local estimates. |
| SYN-PER-01 | BAP-PER-01, BST-PER-01; app and state | One stale-authoring-write contract applied to both features. Crit 5, platform 3, L: **3.8**. Includes client preconditions and app persistence/migration work; state already has a database version. |
| SYN-ABS-01 | BWF-ABS-01, BST-ABS-01; workflow and state | One external-action execution-boundary design, with feature-specific orchestration. Crit 5, platform 3, XL (8 points): **1.9**. A staged remote-effect lifecycle, recovery and compatibility work is larger than either local change. Workflow evidence is High; the state SPI exposure remains Medium because host implementations were not inspected. |
| SYN-LAY-01 | BAP-LAY-01, BDO-LAY-01; app and document | One application-command/mapping boundary pattern applied separately. Crit 3, platform 3, L: **2.3**. Existing graph/value types can be retained; do not invent a shared business-domain model. |
| BRU-MET-01, BRU-PER-01 | rule plus its state/app consumers | Raise blast from feature 2 to platform 3; each becomes **7.5** (5 x 3 / 2). The host composes rule evaluation into multiple features, so executable rule consistency is not confined to rule authoring. |

**Representative evidence retained for merged items:**

- SYN-OPS-01: `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\composition\AiSubjectChangeListener.java:49` acknowledges a failed callback; `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\adapters\inbound\ReleasedObjectsCleaner.java:50` does the same for storage; `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\adapter\inbound\TenantDataCleaner.java:89` uses a one-shot after-commit callback after deleting retry handles. A host replay alone cannot repair all three.
- SYN-PER-01: `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\UpdateAppDefinition.java:52` replaces a whole graph without an expected client revision; `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\UpdateStateMachineDefinition.java:27` loads the latest row before replacing topology, losing the disconnected client's precondition.
- SYN-ABS-01: `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\CompleteTaskUseCase.java:113` invokes tools before durable completion; `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\FireStateTransition.java:66` executes actions before the authoritative state write.
- SYN-LAY-01: `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\UpdateAppDefinition.java:3` and `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\UpdateDocumentProperties.java:3` depend on inbound mapping from the application layer.

Authorization gaps are related but not merged: fixing the shared guard does not add a missing widget call or correct document-specific policy orchestration. Likewise, entity reference discovery and derivative deletion remain separate from durable delivery: a retry cannot recover a reference that was never discovered.

## Ranked work items

Within ties, ordering is for readability, not an additional risk formula. **C/B/E** means criticality / blast weight / effort; source reports supply exact evidence and confidence. All are High confidence except the explicitly retained Medium state-action component of SYN-ABS-01.

| Rank | Work item | Debt / source IDs when merged | C/B/E | Priority |
| --- | --- | --- | --- | --- |
| 1 | PCR-SEC-01 | Implicit permit-all on missing/ambiguous policy wiring | 5/3/M | 7.5 |
| 2 | BRU-MET-01 | Compiled functions survive rule metadata changes | 5/3/M | 7.5 |
| 3 | BRU-PER-01 | CRUD updates executable rules before commit | 5/3/M | 7.5 |
| 4 | PCR-ABS-01 | Identity status tolerance uses arbitrary error text | 4/3/M | 6.0 |
| 5 | BDO-SEC-01 | Alternate document paths omit required policy checks | 5/2/M | 5.0 |
| 6 | BWI-SEC-01 | Widget CRUD lacks use-case authorization | 5/2/M | 5.0 |
| 7 | BWF-PER-01 | MESSAGE delivery lacks atomic occurrence ownership | 5/2/M | 5.0 |
| 8 | API-TST-01 | Specification validation disabled during generation | 3/3/M | 4.5 |
| 9 | API-DUP-01 | Shared schema copies lack structural parity checks | 3/3/M | 4.5 |
| 10 | PST-ABS-01 | Streaming uploads hide full-object buffering | 3/3/M | 4.5 |
| 11 | BAI-PER-01 | Competing job writers can reopen terminal states | 4/2/M | 4.0 |
| 12 | BDO-MET-01 | Live ports invalidate published content bindings | 4/2/M | 4.0 |
| 13 | BEN-MET-01 | Payload shapes disagree with descriptor interpretation | 4/2/M | 4.0 |
| 14 | BEN-ABS-01 | Erasure misses nested embedded artifact references | 4/2/M | 4.0 |
| 15 | BST-OPS-01 | Initialization replay uses a permanently stale event version | 4/2/M | 4.0 |
| 16 | BWF-PER-02 | Join activation precedes progression serialization | 4/2/M | 4.0 |
| 17 | BSR-PER-01 | Destructive import preflight races instance writers | 5/3/L | 3.8 |
| 18 | SYN-PER-01 | Stale authoring writes: BAP-PER-01, BST-PER-01 | 5/3/L | 3.8 |
| 19 | BAI-MET-01 | Durable retries depend on mutable/deletable profiles | 3/2/M | 3.0 |
| 20 | BAP-MET-01 | Publication and runtime disagree on orphan routes | 3/2/M | 3.0 |
| 21 | BEV-TST-01 | Catalog bridge tests bypass durable publication | 3/2/M | 3.0 |
| 22 | BSR-TST-01 | Import test participants cannot roll back definitions | 3/2/M | 3.0 |
| 23 | BWI-API-01 | Widget listing ignores filter/sort arguments | 3/2/M | 3.0 |
| 24 | BWI-ABS-01 | Public widget API exposes persistence types | 3/2/M | 3.0 |
| 25 | OAD-OPS-01 | Invitation failures conceal partially committed work | 3/2/M | 3.0 |
| 26 | PST-ABS-02 | Ordinary deletion leaves thumbnails behind | 3/2/M | 3.0 |
| 27 | PTB-OPS-02 | Persistent databases have no versioned migration path | 4/3/L | 3.0 |
| 28 | SYN-OPS-01 | Durable cleanup: BAI-OPS-01, PST-OPS-01, PTB-OPS-01 | 4/3/L | 3.0 |
| 29 | OAD-SEC-01 | Tenant admin can mutate another tenant's shared account | 5/2/L | 2.5 |
| 30 | OAD-ABS-01 | Concurrent role replacement can produce a union | 5/2/L | 2.5 |
| 31 | SYN-LAY-01 | Inbound-dependent use cases: BAP-LAY-01, BDO-LAY-01 | 3/3/L | 2.3 |
| 32 | BEN-MET-02 | Definition edits lack populated-data compatibility | 4/2/L | 2.0 |
| 33 | BST-MET-01 | Topology edits can strand governed objects | 4/2/L | 2.0 |
| 34 | BWF-MET-01 | Running workflows lack executable definition snapshots | 4/2/L | 2.0 |
| 35 | BWF-OPS-01 | Automatic starts lack durable occurrence outcomes | 4/2/L | 2.0 |
| 36 | BEV-MET-01 | Catalog edits can invalidate live subscriptions | 4/2/L | 2.0 |
| 37 | SYN-ABS-01 | Durable external actions: BWF-ABS-01, BST-ABS-01 | 5/3/XL | 1.9 |
| 38 | BEN-PER-01 | Reference integrity uses unscoped JSON text matching | 3/1/M | 1.5 |

## Strategic debts

These satisfy **criticality >= 4 and effort >= L** after synthesis. A low ratio is a reason to plan the work explicitly, not defer its risk indefinitely.

| Work item | Criticality | Effort | Required design decision |
| --- | --- | --- | --- |
| BSR-PER-01 | 5 | L | Cross-feature exclusion between installs and instance writers |
| SYN-PER-01 | 5 | L | Client preconditions, conflict semantics and independent persistence versions |
| OAD-SEC-01 | 5 | L | Ownership of global identity versus tenant membership |
| OAD-ABS-01 | 5 | L | Cross-process/provider concurrency contract for permission replacement |
| SYN-ABS-01 | 5 | XL | Durable action identities, outcomes, compensation and task failure semantics |
| PTB-OPS-02 | 4 | L | Migration ownership, baseline and populated-database upgrade strategy |
| SYN-OPS-01 | 4 | L | Retryable erasure that survives removal of tenant data and job handles |
| BEN-MET-02 | 4 | L | Compatible schema changes versus explicit data migration/retirement |
| BST-MET-01 | 4 | L | Occupied-state protection or revision-bound state machines |
| BWF-MET-01 | 4 | L | Immutable execution definitions and migration of existing instances |
| BWF-OPS-01 | 4 | L | Start receipts separated from subject-coalescing policy |
| BEV-MET-01 | 4 | L | Subscription compatibility, event revision identity and retirement |

## Proposed refactoring sequence — not implemented

1. **Calibrate and establish the safety baseline.** Estimate a bounded rule-cache fix, a stale-write contract change and the durable-action project with the owner before treating this ranking as a delivery plan. Run the existing targeted tests/Modulith checks when implementation starts. Add BEV-TST-01 and BSR-TST-01 fixtures early, plus deterministic contention tests beside each concurrency fix; test architecture work can run in parallel with authorization and rule-cache work.
2. **Close critical reusable mechanism gaps.** Address PCR-SEC-01, BWI-SEC-01 and BDO-SEC-01 together as a policy audit, preserving explicit demonstration mode. Fix BRU-MET-01 and BRU-PER-01 together so both committed metadata and per-thread execution converge. PCR-ABS-01 is an independent core/identity change; coordinate its exception contract with organization adapters. Plan OAD-SEC-01 and OAD-ABS-01 now despite their larger scope.
3. **Protect writes and runtime decisions.** Implement BWF-PER-01 before BWF-OPS-01; share occurrence conventions but keep start and catch receipts distinct. BWF-PER-02 and BAI-PER-01 can proceed in parallel in separate aggregates. Complete BSR-TST-01 before accepting BSR-PER-01's race guarantee. Implement SYN-PER-01 before BST-MET-01's revision-aware authoring decisions; add schema changes through the migration baseline from PTB-OPS-02 rather than relying on production `ddl-auto`.
4. **Make delivery and erasure recoverable.** Design SYN-OPS-01 as an end-to-end pipeline: host acceptance, feature cleanup intent and storage execution each need a durable outcome. BEN-ABS-01 and PST-ABS-02 can be implemented in parallel because they determine which resources are owned, not how retry works; all three are needed before claiming complete erasure. BST-OPS-01 can reuse tested replay conventions without waiting for the external-action project. Design SYN-ABS-01 separately and deliver it in stages with explicit remote idempotency/compensation semantics.
5. **Define metadata evolution, then apply it per feature.** BEN-MET-01 precedes BEN-MET-02; SYN-PER-01 precedes the state portion of BST-MET-01. Coordinate BWF-MET-01 and BEV-MET-01 on revision/retirement identity, with BEV-TST-01 covering changed delivery semantics. BAI-MET-01 and BDO-MET-01 can proceed independently as accepted-job and published-content consistency fixes. Do not force every metadata type into the same snapshot implementation.
6. **Tighten contracts and adapters in parallel where independent.** API-TST-01/API-DUP-01, PST-ABS-01, BWI-API-01 and BAP-MET-01 are bounded streams independent of the larger lifecycle projects. Narrow BWI-ABS-01's public surface with consumer checks. Apply SYN-LAY-01 after capturing existing behavior; it is not a prerequisite for fixing authorization or data integrity. OAD-OPS-01 and BEN-PER-01 retain their own provider/reference semantics rather than being hidden inside a general cleanup refactor.

This is a recommendation and dependency map only. No fixes, migrations, new runtime tooling or commits were made.

## Limits and retained strengths

Confidence reflects code evidence, not successful reproduction under production load. In particular, the state action finding is an SPI risk with uninspected host implementations; shared Keycloak membership is a supported adapter branch, not a confirmed production account arrangement; and missing slice-local tests do not prove that no downstream application has compensating coverage. The testbed has existing full-composition and event tests, but failure/restart and competing-transaction guarantees require their own assertions.

Keep the deliberate module allow-lists and named ports, composition-root adapters, tenant-scoped persistence queries, public/draft document separation, commit-aware import SPI, explicit event identities, timer eligibility rechecks and AI polling fallback. The proposed work strengthens these structures rather than replacing the modular-monolith architecture.
