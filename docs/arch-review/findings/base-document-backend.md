# Architecture review: base-document-backend

- **Slice code:** BDO
- **Paths in scope:** `libs\java-shared\base-document-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Static review using IDE directory/source inspection, scoped ripgrep, PowerShell read-only source inspection and file reads. Read `AGENTS.md`, the rubric and relevant README intent. Inspected publication, public-read, import, properties, persistence and Modulith tests; did not execute tests, builds, ArchUnit or Modulith verification. No Nx configuration was needed or read.
- **Calibration:** Reviewer/owner calibration was not performed. Effort estimates are provisional; rubric scales and effort points are unchanged.

## Remediation log

### BDO-SEC-01: addressed (2026-10-11)

Private published reads now enforce organization membership before reader roles. Membership denial
uses the existing not-found response and never loads snapshots; provider failures still propagate
instead of being hidden as denials. Public documents retain anonymous access. Import preflights
every existing target against its stored editor roles before mutating or saving any entry, so role
replacement cannot authorize its own overwrite and mixed-authority files leave all entries intact.

**Validation:** 35 focused tests passed through Nx `base-document-backend:test`, including public
reads, imports and `BaseDocumentModularityTests`. Five new regression cases failed against the
original implementation before the fix; public access and authorized imports remain supported.

The branch/test/commit/PR/merge workflow is now recorded in root `AGENTS.md` for future sessions.

### BDO-MET-01: addressed (2026-10-11)

Properties replacement, full updates and imports now share `checkPortChange`: proposed ports must
validate every resulting draft (including retained locales) and every extant published snapshot.
Published failures identify the locale and publication in their diagnostics. Import preflights
all targets before writing, preserving all-or-nothing behavior. Snapshots remain untouched; remove
their bindings through republishing or unpublish the affected locales before removing live ports.

**Validation:** 83 focused tests passed through Nx, including module boundaries and the shared
integrity checker. Three regressions first failed on the original code: publish/edit/remove-port
with two locales and public readback, full replacement of draft bindings while a snapshot retains
them, and an import invalidating an omitted draft. Further tests cover published input/output
bindings during import and valid removal after snapshots are withdrawn. No concurrency or
live-provider tests were added; ports remain live rather than versioned with each publication.

The original assessment and evidence below are retained as the review baseline.

## Summary (review baseline)

The document model has useful structural boundaries: language-invariant metadata, editable translations and published content occupy separate entities, and the public reader cannot access the draft repository. The most important debt is incomplete use-case enforcement of the module's own access policy, affecting private reads and imports even when a restrictive policy is supplied. Published content can also become inconsistent with subsequently edited port metadata because the integrity boundary checks drafts, not snapshots. A further layering debt couples application commands to REST/YAML representations despite the stated hexagonal design.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BDO-SEC-01 | Alternate access paths bypass document policy checks | SEC | 5 | 2 feature | M | High | 5.0 | Addressed 2026-10-11 |
| BDO-MET-01 | Live port metadata can invalidate published snapshots | MET | 4 | 2 feature | M | High | 4.0 | Addressed 2026-10-11 |
| BDO-LAY-01 | Use cases depend on inbound mappers and wire models | LAY | 3 | 1 slice | L | High | 0.8 | Open |

## Findings

### BDO-SEC-01 — Alternate access paths bypass document policy checks

- **Category:** SEC
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\port\DocumentAccessPolicy.java:27` — `requireAccess(orgKey)` is the membership check; authentication and role checks are separate methods.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\service\DocumentGuard.java:57` — private `canRead` checks authentication and reader roles but never organization membership; `requireEditor` at line 68 does check membership and editor roles.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\FindPublishedContent.java:47` — the public read uses `canRead` as its sole policy gate before loading snapshots.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\ImportDocuments.java:74` — import requires organization access only; `applyToExisting` at line 137 replaces an existing document's properties, roles and drafts without `requireEditor`.
  - `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\usecase\FindPublishedContentTest.java:129` — the positive restricted-reader fixture overrides roles but leaves membership unrestricted at lines 186–193.
  - `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\usecase\ImportDocumentsTest.java:354` — the inspected import access test covers organization membership, not per-document editor authorization.
- **Why it is debt:** Access checks exist centrally but are not applied consistently across alternate paths. An authenticated nonmember with matching reader roles can satisfy the private published-read gate, while an organization member without a document's editor role can overwrite that document through import, including its access metadata. These are gaps in the module-owned policy orchestration, independent of the separately documented permit-all default; external HTTP filters may compensate but were not inspected. The feature-wide blast includes private content and editorial permissions exposed to its clients.
- **Suggested refactoring:** Make the private-read decision enforce membership before reader roles and translate membership denial into the same not-found result used for other private-read denials. Preflight every existing import target with `requireEditor` before mutating any entry, preserving all-or-nothing behavior. Add restrictive-policy tests for nonmember authenticated readers, members lacking editor rights, role-metadata overwrite attempts and mixed-authority import files.
- **Depends on / blocks:** none

### BDO-MET-01 — Live port metadata can invalidate published snapshots

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\PublishDocumentTranslation.java:68` — publication checks draft bindings against current document ports, then snapshots content at lines 76–81.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\UpdateDocumentProperties.java:67` — a port replacement checks all drafts, but no published snapshots, before replacing metadata at lines 79–88.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\adapter\inbound\DocumentMapper.java:253` — the public response combines snapshot blocks at lines 267–268 with live document input/output ports at lines 269–270.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\service\DocumentReferentialIntegrityChecker.java:147` — a binding to an undeclared port is an integrity error.
  - `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\usecase\UpdateDocumentPropertiesTest.java:90` — tests cover an untouched draft binding; the fixture at line 155 supplies no published snapshots.
- **Why it is debt:** The persisted/public composition spans two versioning boundaries, but validation protects only the draft side. Publish a block bound to port P, remove that binding in the draft, then remove P through the properties command: draft validation succeeds while the public response still contains the published binding and no declaration for P. This violates the feature's referential-integrity promise and can break already published widget content without a publication action.
- **Suggested refactoring:** Centralize port-change validation and reject changes that invalidate any extant published snapshot as well as any retained draft; apply it to properties replacement, full update and import. Add the publish/edit/remove-port/public-read regression sequence, including multiple locales. If independent draft port editing is required later, version the binding-relevant ports with the published content rather than mixing live and snapshotted definitions.
- **Depends on / blocks:** none

### BDO-LAY-01 — Use cases depend on inbound mappers and wire models

- **Category:** LAY
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** L
- **Confidence:** High
- **Priority score:** 0.8
- **Evidence:**
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\UpdateDocumentProperties.java:3` — imports inbound `DocumentMapper`; the public command at line 58 takes generated `DocumentPropertiesInput`.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\ImportDocuments.java:6` — imports the inbound mapper and generated document input models; YAML deserialization takes place inside the transactional use case at line 77.
  - `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\adapter\inbound\DocumentMapper.java:22` — the inbound mapper also depends on use-case result/validation types, completing a package-level dependency in both directions.
  - `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\usecase\UpdateDocumentPropertiesTest.java:52` — application behavior tests construct the real inbound mapper to execute the use case.
  - `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\BaseDocumentModularityTests.java:12` — inspected architecture verification checks module structure, not these internal layers.
- **Why it is debt:** The application layer depends on the adapter meant to translate into it, so generated REST models and YAML decoding are part of the feature's orchestration API. The coupling is concrete in both production commands and their tests, not merely a package naming concern. It makes another transport or changes to contract generation require application-layer edits even though the domain already has document, port and content value types.
- **Suggested refactoring:** Introduce application-owned property, translation and import commands using the existing domain values. Decode/map HTTP and YAML at inbound boundaries and pass complete validated command data into transaction-owning use cases. Keep response mapping in the adapter and enforce the dependency direction with an architecture test.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host security filters, identity-provider implementations, generated OpenAPI source and frontend binding resolution were not inspected and are unscored. The authorization finding concerns the supplied policy contract inside this slice, not a claim about a deployed endpoint's exploitability.
- `libs\java-shared\base-document-backend\README.md:38` explicitly documents permissive defaults; `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\service\DocumentGuard.java:41` selects that fallback. Whether an actual deployment supplies a unique restrictive adapter remains outside this review.

## Strengths worth keeping

- `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\FindPublishedContent.java:32` injects no draft repository; `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\usecase\FindPublishedContentTest.java:45` explicitly guards this structural separation.
- `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\domain\DocumentDraft.java:64` separates editorial revision from `lockVersion`; `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\domain\DocumentPersistenceTest.java:98` tests their independent movement.
- `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\service\DocumentDraftEditor.java:55` centralizes load/authorize/mutate/validate/save for block edits instead of duplicating integrity gates.
- `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\usecase\PublishDocumentTranslation.java:86` emits a publication fact without calling a workflow feature directly.
- `libs\java-shared\base-document-backend\src\main\java\com\processpuzzle\document\package-info.java:23` declares a core/shared allow-list; `libs\java-shared\base-document-backend\src\test\java\com\processpuzzle\document\BaseDocumentModularityTests.java:15` supplies verification, inspected but not executed here.
