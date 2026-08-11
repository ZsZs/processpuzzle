# ProcessPuzzle :: Base Document Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-document-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_document_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_document_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/base-document-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/base-document-backend)

ProcessPuzzle Base Document Backend is the server-side companion of [`@processpuzzle/base-document`](../../js-shared/base-document-frontend/README.md). It is the structural persistence for wiki-style textual documents (project plans, design docs, reports) composed of a flat, ordered list of TEXT and WIDGET blocks, in a Spring Boot application following the platform's Hexagonal architecture.

The module owns **only** document/block structure, widget component references and their static props, declared input/output ports, and opaque Tiptap JSON content. It has no knowledge of the runtime data flowing through a widget's bindings — resolving those to actual values happens entirely in the frontend runtime interpreter. It never queries `base-entity` or fetches data itself; it only records that a wiring exists.

Unrelated to `base-entity`'s `ARTIFACT` form control, which handles file and blob attachments through `processpuzzle-store`.

## Model

Three entities, split along the two axes that actually vary — language, and publication:

| Entity | Key | Holds |
| --- | --- | --- |
| `Document` | (`orgKey`, `id`) | language-invariant metadata, ports, access control |
| `DocumentDraft` | (`orgKey`, `documentId`, `locale`) | the editable block list, per locale |
| `PublishedDocument` | (`orgKey`, `documentId`, `locale`) | the snapshot readers are served, per locale |

**Identity.** `id` is a server-assigned UUID, so a document is handled like any other ProcessPuzzle entity and internal references survive a rename. `slug` is a separate human-readable route key, unique per organization, and is what a reader's URL carries.

**Multi-language.** Metadata is invariant — it is editor-facing. Only block content varies by locale. So there is one URL per document and the reader's language is negotiated by the application, falling back to `sourceLocale`; the reader-facing heading comes from the content's own first heading block, not from `title`. Each translation records the `basedOnRevision` of the source it was made from, which is what makes a stale translation detectable.

**Publishing** is per locale, so one language can go live while another is still being translated. A draft carries a plain `revision` counter; a snapshot records the `publishedRevision` it was taken from, and `DocumentStatus` is derived by comparing them rather than stored. Unpublishing deletes the snapshot, so "withdrawn" needs no extra state. Publishing emits `DocumentPublished`, so an approval flow can attach later without this module knowing it exists.

Two deliberate divergences from `base-app`'s `AppDefinition`, which is otherwise the model this follows:

- **The published snapshot is its own entity**, not a second column beside the draft. Document content is read anonymously, and this is what makes "a draft is never publicly readable" a property of what the public path reads rather than a check someone has to remember. It is also what survives the port to a store that authorizes whole records and cannot hide a field.
- **Publishing is editorial, not configuration**, so a document is deliberately not a candidate for any organization-wide configuration release train.

## Authorization

`isPublic` plus `readerRoles` / `editorRoles` / `publisherRoles` live on the document as data, so a store that authorizes with rules can evaluate them with no server in the path. Reading resolves in one order: `isPublic` first (anyone, unauthenticated), then organization membership, then the reader roles — where an empty list means "any authenticated member", the convention `NavNode.roles` sets in base-app. Editing and publishing never take the first rung: a public document is still edited only by its editors. An empty `publisherRoles` falls back to `editorRoles`.

Identity itself is supplied by the deploying application through the `DocumentAccessPolicy` outbound port, which defaults to permitting everything — the same arrangement base-app uses, and for the same reason: there is no Spring Security on this library's classpath. This module declares its *own* port rather than reusing base-app's identical one, because base-document must not depend on base-app: the app shell is expected to host documents as its primary content type, so that edge would eventually close a cycle.

A reader who may not see a document gets **404, not 403** — telling them it exists is what a restricted document is meant to withhold. 403 is reserved for a caller who demonstrably knows it exists, such as an editor without publish rights.

## Technologies

- **Java 25**
- **Spring Boot 4**
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Status

Under construction. The domain, the use cases and the inbound adapter are in place, and the REST surface is generated from `base-document-api.yaml` in `api-contracts` — 21 operations covering CRUD, per-locale translations, per-locale publishing, block-level editing, validation, YAML import/export and the anonymous public read.

Not yet done: the frontend half is still on the previous shape (`@processpuzzle/base-document` needs the metadata form, a Content tab and a persistence-agnostic content port), there is no sample data loader, and no `DocumentAccessPolicy` implementation exists anywhere yet — so role checks currently permit everything, by design, until an identity provider is wired into the backend.

## Development

```powershell
npm exec nx build base-document-backend
npm exec nx test base-document-backend
npm exec nx lint base-document-backend
```

## License

This project is licensed under the Apache License 2.0.
