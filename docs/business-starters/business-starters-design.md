# ProcessPuzzle Business Starters — Design Sketch

## 1. Goal

A **Business Starter** is a prepared solution to one business problem (inventory, billing, organising a sail race, ...). It is a bundle of YAML definitions: Entity, State, Rule, Document, Workflow and App.

Requirements:

- Starters are authored and stored in the `processpuzzle-biz` repo.
- A customer can import one or more starters.
- When an application seed is created, the chosen starter(s) are already imported.
- Import target is the customer's DB, keyed by `{orgKey}`.
- The **Design** view can import a starter and ideally export a modified or custom-developed one.

## 2. Decision

**Git is the source of truth. A generic import API in a new `base-starter-backend` library does the importing. MinIO (S3) is the optional distribution store in between.**

| Layer | Responsibility | Where |
|---|---|---|
| Authoring | Write, review and version starters | Git, `processpuzzle-biz/starters/<id>/` |
| Distribution | Validated, versioned bundles + catalog index | CI, then MinIO bucket `starters/` (or baked into the backend image for a small catalog) |
| Import / Export | Validate, order, write definitions for one org | `base-starter-backend` + `base-starter-frontend`, orgKey-scoped |
| Catalog | Browse, select and install starters | `processpuzzle-biz` (UI and catalog endpoints) |
| Billing | Payments and bills for starters | Admin (no install-time payment check for now) |

## 3. Authoring layout

```
processpuzzle-biz/
  starters/
    sail-race-organizer/
      manifest.yaml
      entities/   states/   rules/
      documents/  workflows/  apps/
      data/       (optional seed data)
      icons/
```

CI steps per release: validate every YAML against the definition schemas, check cross-references, compute the integrity hashes, zip into `<id>-<version>.zip`, upload to MinIO, regenerate `catalog.json`.

The full manifest sketch is in `starter-manifest.example.yaml`.

## 4. Import API (base-starter-backend)

The importer takes **bytes, not a storage location**. That keeps it independent of MinIO, Git or the file system. Full contract: `definitions-import-api.yaml`.

| Endpoint | Purpose |
|---|---|
| `GET /organizations/{orgKey}/starters` | Installed starters with provenance |
| `POST /organizations/{orgKey}/definitions/import` | Import an uploaded bundle (`dryRun` supported) |
| `GET /organizations/{orgKey}/definitions/export` | Export definitions as a bundle |

The catalog endpoints (`GET /starters`, `GET /starters/{id}/versions/{v}`) and *install from catalog* belong to
`processpuzzle-biz`: install there means fetching the bundle bytes and calling `definitions/import`.

One importer, three callers:

1. **Design view** — "install from catalog" or file upload, with a dry-run preview first.
2. **Seed provisioning** — fetch the chosen bundle(s), call the importer for the new `{orgKey}`.
3. **AI definition generator (future)** — its output is just another bundle.

Export is the reverse operation, so exporting a customized or custom-developed starter comes almost for free.

## 5. Importer rules

- **Order:** Entity, State, Rule, Document, Workflow, App.
- **Atomic:** validation of schema and cross-references happens before any write; all writes in one transaction.
- **Idempotent with provenance:** every imported definition is tagged with `starterId`, `starterVersion` and a content hash. Re-import is safe, and "untouched" can be told apart from "customized".
- **Upgrades:** newer starter version overwrites untouched definitions and flags customized ones as conflicts. Strategy: `skip-customized` (default), `overwrite`, `fail`.
- **Schema versioning:** `definitionSchemaVersion` in the manifest lets the importer migrate or reject old bundles.
- **Dependencies:** `requires` lets starters share base entities (e.g. a contacts starter); the importer resolves and installs them first.
- **Trust:** treat every uploaded bundle as untrusted input — schema-validate, size-limit, and make sure rule and expression definitions cannot execute anything unsafe.
- **Dry run:** always available, returns an `ImportReport` listing create/update/skip/conflict per definition.

## 6. Where things live

- **Live, customer-modified definitions:** the customer's DB (keyed by `{orgKey}`). This is the working copy.
- **Official starters:** public/read-only `starters/` bucket (or image).
- **Exports:** plain downloads. For org-private backup or sharing later, use a separate `orgs/{orgKey}/` prefix, never the official bucket.
- **Customer-contributed marketplace (later):** separate, reviewed pipeline.

## 7. Decisions (2026-10-05)

1. **Catalog in Biz, billing in Admin.** The catalog UI and catalog endpoints live in `processpuzzle-biz`;
   administering payments and bills stays in Admin. No upfront payment check at install time for now.
2. **New library pair.** Orchestration lives in `base-starter-backend`, the import/export UI in
   `base-starter-frontend`. Existing feature libraries stay focused.
3. **No compile edges.** `base-starter-backend` depends on no feature library. `processpuzzle-core` declares a
   `DefinitionImportParticipant` SPI; each feature implements it for its own definitions; the orchestrator
   collects the participants as beans and runs them in order.
4. **Bundle files use the existing seed YAML formats** (`DefaultEntitiesDocument` and its siblings). The
   classpath seed loaders become one more caller of the importer.
5. **One transaction** across participants — valid while all modules share one DataSource. Revisit when a
   feature really becomes a microservice.
6. **Provenance in one importer-owned table**, keyed by `(orgKey, kind, key)`, holding `starterId`,
   `starterVersion` and the content hash. Feature tables are not changed; "customized" means the
   participant's canonical export no longer hashes to the stored value.
7. **Definition kinds and order:** Entity, State, Rule, Widget, Document, Workflow, App; translations travel
   with their kind.

## 8. Suggested next steps

1. Write the contract in `api-contracts` (OpenAPI 3.0.3); validation reuses the existing `*Input` schemas.
2. Build the SPI in `processpuzzle-core` and the orchestrator in `base-starter-backend`, dry-run report first;
   entity and rule participants first, the other kinds after.
3. Create one small pilot starter (e.g. inventory) and run it end to end through CI, bucket and import.
4. Add the Design view import/export UI.
5. Hook the importer into the customer-seed provisioning flow.
