# Architecture review: base-entity-backend

- **Slice code:** BEN
- **Paths in scope:** `libs\java-shared\base-entity-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Static review using IDE file discovery, scoped glob/ripgrep, numbered PowerShell reads, file reads and Git revision verification. Read `AGENTS.md`, `README.md` architectural intent and the rubric. Inspected definition/instance models, mutation and validation paths, published object access, scope handling, JSONB query interpretation, tenant cleanup, module declarations and relevant tests. Tests inspected, not executed; no build, Nx task, ArchUnit or Modulith verification executed.
- **Estimation note:** Effort is provisional: rubric calibration has not been completed. Scales and effort points are unchanged.

## Summary

The slice has substantive metadata-driven behavior and useful ports between definitions, instances and external attribute owners; it is not merely a scaffold. Its highest-priority gaps are inconsistent interpretation of payload shape (BEN-MET-01) and incomplete discovery of embedded artifact references during erasure (BEN-ABS-01). Schema mutation also changes the meaning of existing payloads without a population-aware compatibility boundary (BEN-MET-02). A lower-priority JSON-text reference guard substitutes substring matching for metadata-defined relationships (BEN-PER-01). These are concrete representation and lifecycle problems, not objections to JPA annotations or intentional scaffold absence.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BEN-MET-01 | Payload validation does not enforce the representation it interprets | MET | 4 | 2 feature | M | High | 4.0 |
| BEN-ABS-01 | Tenant erasure misses artifacts inside embedded components | ABS | 4 | 2 feature | M | High | 4.0 |
| BEN-MET-02 | Schema edits are disconnected from existing payload compatibility | MET | 4 | 2 feature | L | High | 2.0 |
| BEN-PER-01 | Reference integrity is approximated by a global text search | PER | 3 | 1 slice | M | High | 1.5 |

## Findings

### BEN-MET-01 — Payload validation does not enforce the representation it interprets

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\DefaultPayloadValidatorAdapter.java:46` — required presence is checked, but non-embedded values immediately return without checking `valueKind` or collection shape.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\DefaultPayloadValidatorAdapter.java:68` — embedded lists are checked only at the outer level; each row is then cast directly to `Map`.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\DefaultPayloadValidatorAdapter.java:79` — a non-map single embedded value is silently accepted, so its child's required fields are never checked.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\CreateEntityInstanceUseCase.java:48` — after validation, the supplied payload is saved directly; `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\UpdateEntityInstanceUseCase.java:70` does the same for updates.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\rsql\JsonPathExpressionBuilder.java:85` — search creates numeric and boolean comparison values from the declared kind, relying on payloads using compatible JSON types.
  - `libs\java-shared\base-entity-backend\src\test\java\com\processpuzzle\baseentity\instances\adapters\outbound\DefaultPayloadValidatorAdapterTest.java:220` — the inspected test explicitly accepts `"not-a-map"` as an embedded value rather than requiring rejection.
- **Why it is debt:** The write boundary accepts representations that the metadata-driven reader and nested validator do not interpret consistently. For example, a required embedded object supplied as a nonblank string bypasses its child's requirements, while a scalar inside an embedded list produces a cast failure instead of a validation violation; a string in a NUMBER field is saved despite numeric query semantics. This makes the same descriptor mean different things during persistence, validation and search, affecting consumers of the generated entity feature.
- **Suggested refactoring:** Establish one structural representation contract for the existing attribute view: validate scalar kinds, single/list shape and every embedded row before recursion. Return path-qualified violations for malformed shapes rather than ignoring them or casting unchecked. Add malformed single-component, heterogeneous-array, null-row and scalar-kind tests, plus write/search compatibility coverage; keep business-rule evaluation as a separate extension of structural validation.
- **Depends on / blocks:** none

### BEN-ABS-01 — Tenant erasure misses artifacts inside embedded components

- **Category:** ABS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\CreateEntityInstanceUseCase.java:42` — embedded definitions cannot have independent instances; their data lives in a parent payload.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\DefaultPayloadValidatorAdapter.java:55` — embedded data is interpreted recursively using the linked child definition.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\adapter\inbound\TenantDataCleaner.java:88` — artifact discovery groups only definitions with directly declared ARTIFACT attributes.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\adapter\inbound\TenantDataCleaner.java:97` — it fetches objects of those definition codes and reads each artifact by a top-level payload key, without traversing embedded components.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\adapter\inbound\TenantDataCleaner.java:71` — references are collected once before all tenant attributes, definitions and objects are erased and the collected release is published.
  - `libs\java-shared\base-entity-backend\src\test\java\com\processpuzzle\baseentity\adapter\inbound\TenantDataCleanerTest.java:212` — inspected cleanup fixtures place single/list ARTIFACT attributes directly on a document definition, not in a linked embedded definition.
- **Why it is debt:** Lifecycle code uses a flatter data model than the instance feature itself. If a parent contains an embedded child whose attribute is an ARTIFACT, the child code is selected for scanning but has no instance rows, while the parent's embedded path is never visited. Erasure then deletes the owning payload and metadata without releasing that file reference, affecting both entity lifecycle completeness and the storage feature that can only act on references it receives.
- **Suggested refactoring:** Extract a metadata-guided artifact-reference walker that starts from actual root objects and recursively follows embedded single/list components, collecting only ARTIFACT values. Use it before erasure, with cycle/depth protection and deduplication. Add committed and rolled-back tenant-erasure tests for nested components, including parents with no directly declared artifact attributes.
- **Depends on / blocks:** none

### BEN-MET-02 — Schema edits are disconnected from existing payload compatibility

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\definition\usecases\inbound\DeleteAttributeUseCase.java:28` — the populated-attribute guard is deferred; the attribute is removed and the definition saved without checking instances.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\definition\usecases\inbound\ReplaceEntityDefinitionUseCase.java:53` — full replacement also removes absent attributes; lines 64–79 change kind, multiplicity, required status and linkage in place.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\definition\usecases\inbound\ReplaceAttributeUseCase.java:30` — individual replacement updates the same structural fields and validates only the resulting definition.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\definition\domain\EntityDefinitionValidator.java:20` — validation checks the definition graph itself, not compatibility with populated objects.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\rsql\AttributePathResolver.java:39` — a removed attribute can no longer be resolved for search, although its JSONB value remains stored.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\adapter\inbound\TenantDataCleaner.java:88` — file-reference discovery depends on current attribute metadata, so retiring a populated ARTIFACT attribute also removes the information this cleanup path uses.
  - `libs\java-shared\base-entity-backend\src\test\java\com\processpuzzle\baseentity\definition\usecases\DefinitionUseCasesTest.java:244` — inspected attribute-deletion coverage verifies definition mutation with mocked collaborators rather than populated-instance compatibility.
- **Why it is debt:** Metadata is the runtime schema, but editing that schema has no boundary that protects or migrates the data it already describes. Removing a populated attribute leaves stored values that metadata-based search no longer recognizes, and removing an ARTIFACT declaration makes those old values invisible to the current erasure scan. These are consequences of existing successful mutation paths, not a request to implement a speculative schema designer; future clients inherit the same inconsistent representation.
- **Suggested refactoring:** Introduce a definition-evolution service and outbound population-inspection port shared by full replacement, attribute replacement and deletion. Classify compatible changes, reject destructive populated changes by default, and support explicit migrations or staged retirement where values and owned file references are transformed before metadata disappears. Validate migrated payloads using the structural contract from BEN-MET-01 and add populated-object regression tests for every mutation entry point.
- **Depends on / blocks:** BEN-MET-01

### BEN-PER-01 — Reference integrity is approximated by a global text search

- **Category:** PER
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 1.5
- **Evidence:**
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\domain\EntityObjectRepository.java:26` — the delete guard casts every payload to text and searches for the target ID as a substring, without organization, attribute or relationship constraints.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\DeleteEntityInstanceUseCase.java:29` — a scoped target lookup is followed by that global query; any match produces a reference-conflict response unless `cascade=true`.
  - `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\domain\EntityObjectScope.java:43` — ordinary lookup explicitly separates organization and entity type, unlike the reference guard.
  - `libs\java-shared\base-entity-backend\src\test\java\com\processpuzzle\baseentity\instances\usecases\InstancesUseCasesTest.java:262` — inspected reference tests stub the repository's boolean and do not exercise actual JSONB relationships or non-reference text.
- **Why it is debt:** Relationship ownership is defined by metadata, yet this integrity decision treats arbitrary serialized content as a foreign key. A note containing an object's UUID, including a note in another organization, can block deletion even when no reference attribute points to it. The text-based query also bypasses the slice's central scope policy and makes the override flag the workaround for false conflicts rather than a deliberate relationship operation.
- **Suggested refactoring:** Replace the substring query with a scoped reference-query port that resolves reference attributes and their JSON paths from metadata and compares complete reference values. Specify same-organization and any intentionally shared-reference rules explicitly, excluding unrelated text and self/non-reference matches. Add PostgreSQL-backed cases for real references, UUIDs in free text, another tenant and nested reference values.
- **Depends on / blocks:** none

## Out-of-scope observations

- Deploying-application validator replacements, authorization, state/rule adapters, external event consumers and frontend descriptor handling were not inspected. Findings about validation describe this slice's concrete default implementation, not every possible deployment.
- Storage's ability to retry a correctly emitted release is a separate issue, PST-OPS-01. Nested reference discovery and delivery reliability can be improved independently; both are necessary for complete erasure.

## Strengths worth keeping

- Definitions are consumed through a narrow projection rather than a JPA association: `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\outbound\EntityAttributeView.java:8` and `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\domain\EntityObject.java:23`.
- Public object access reuses organization/type scoping and compares expected versions before a single-attribute write: `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\adapters\outbound\EntityObjectAccessAdapter.java:52`. Its deliberate no-event privileged channel is documented at line 21 rather than hidden in a repository shortcut.
- Generic updates preserve attributes owned by another feature through a port: `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\UpdateEntityInstanceUseCase.java:91`.
- Mutation events are published inside transactional use cases after create/update flush supplies persisted IDs and versions: `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\CreateEntityInstanceUseCase.java:50` and `libs\java-shared\base-entity-backend\src\main\java\com\processpuzzle\baseentity\instances\usecases\inbound\UpdateEntityInstanceUseCase.java:74`. This preserves a useful transaction/event seam without assuming external delivery configuration.
- The tenant-cleanup tests exercise real commit/rollback release behavior (`libs\java-shared\base-entity-backend\src\test\java\com\processpuzzle\baseentity\adapter\inbound\TenantDataCleanerTest.java:152` and line 168), and a module-verification test exists at `libs\java-shared\base-entity-backend\src\test\java\com\processpuzzle\baseentity\BaseEntityModularityTests.java:12`. Neither was executed during this review.
