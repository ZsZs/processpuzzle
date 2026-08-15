# base-entity-backend

Runtime metadata-driven entity platform, hexagonal layout, two Spring Modulith application
modules: **definition** (was "knowledge layer") and **instances** (was "operation layer").

Each module:

```
adapters/
  inbound/   — REST endpoints + DTOs + mappers (dtoToEntity / entityToDto)
  outbound/  — implementations of this module's own outbound ports, including the ones that
               reach into the OTHER module (definition <-> instances talk only through ports)
usecases/
  inbound/   — one class per action (CreateXUseCase, FindXByIdUseCase, ReplaceXUseCase, ...)
  outbound/  — port interfaces this module's use cases need from outside itself
domain/
  — JPA entities + Spring Data repositories
```

## Cross-module boundary

definition and instances never depend on each other's `domain` package directly through a
usecase — only through a port + adapter pair:

- **definition → instances**: `EntityInstanceExistenceCheckPort` (definition/usecases/outbound),
  implemented by `EntityInstanceExistenceCheckAdapter` (definition/adapters/outbound). Used by
  `DeleteEntityDefinitionUseCase`'s guard.
- **instances → definition**: `EntityDefinitionLookupPort` (instances/usecases/outbound),
  implemented by `EntityDefinitionLookupAdapter` (instances/adapters/outbound). Used by payload
  validation and RSQL attribute-path resolution. Deliberately returns `EntityDefinitionView` /
  `EntityAttributeView` — small read-only records owned by the instances module — rather than
  definition's JPA entities, so instances never has a compile-time dependency on definition's
  domain shape.

**Known gap**: both adapters currently still autowire the other module's `domain` repository
directly to implement their side of the port. That compiles and works in a single-deployable
monolith, but doesn't yet respect Spring Modulith's default encapsulation (only a module's root
package is visible to others unless a subpackage is opened via `@NamedInterface`). Both modules
have `package-info.java` declaring `@ApplicationModule`; running
`ApplicationModules.of(BaseEntityApplication.class).verify()` will very likely fail on this until
either `@NamedInterface` is added to the relevant `domain` packages or the adapters are reworked
to call a narrower published API on the other module instead of its repository.

## Still pending on your side

- **`RsqlSpecificationBuilder`** (processpuzzle-core) — I couldn't fetch its source (GitHub
  rejected both the raw and blob URLs even when you linked them directly). Everything in
  `instances/adapters/outbound/rsql/` is a self-contained, functional-but-provisional
  reimplementation (RSQLParser + hand-rolled AND/OR/comparison visitor) that almost certainly
  duplicates parsing/composition logic already in core. Once you can paste that file's contents
  (signature, entry method, any property-resolution extension point), `RsqlToInstanceSpecificationAdapter`
  should be rewritten to extend/wrap it — the only genuinely novel part to keep is the
  `jsonb_path_exists`-based JSONB predicate construction for a single comparison.
- **Exceptions / logging** — `common/NotFoundException`, `ConflictException`,
  `ValidationException`, `GlobalExceptionHandler`, and the auditing base class are all local
  placeholders with `TODO` headers, on the same basis: you said these live in
  `processpuzzle-core` but I don't have that source either. Same ask — point me at (or paste)
  the actual classes and these get deleted in favor of them.
- **`base-rule-backend` package conventions** — I worked from your prose description of the
  adapters/usecases/domain split (confirmed against what you said exists in base-rule-backend),
  not the actual file listing, since GitHub's tree view is blocked for automated fetches here
  too. If naming conventions there differ from what I've used (e.g. UseCase-as-interface instead
  of a single concrete class, or a different mapper naming pattern), point out the mismatch and
  I'll conform this module to match exactly.

## Everything else, as before

- `FormControlType` values beyond `FOREIGN_KEY` / `EMBEDDED_COMPONENTS` / `ARTIFACT` are
  placeholders pending `abstact-attr.descriptor.ts`.
- `BaseEntityAttribute.indexed` has no auto-generated expression index yet — see the commented
  example at the bottom of `V1__base_entity_schema.sql`.
- `EntityObjectRepository.existsAnyReferenceTo` is an unindexed `payload::text like '%id%'`
  check — fine at current volumes, worth tightening once a specific FOREIGN_KEY attribute needs
  a fast reverse lookup.
- `pom.xml` `<parent>` coordinates are placeholders.
