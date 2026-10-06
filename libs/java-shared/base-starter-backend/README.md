# base-starter-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-starter-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_starter_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_starter_backend)

## Introduction

`base-starter-backend` serves the **Business Starter** catalog and installs a starter from it into one
organization. A starter is a bundle — a zip with a `manifest.yaml` at its root and definition files in each
feature's own seed YAML format, the same files the classpath seed loaders read — that CI publishes to the
public `processpuzzle-starters` bucket. It is a Spring Modulith application module (`starter`) that depends on
no feature library. Design: [business-starters-design.md](../../../docs/business-starters/business-starters-design.md).

## The registry

The bucket *is* the registry: `catalog.json` lists every starter and version, and each version lives at
`<id>/<version>/bundle.zip`. `HttpStarterRegistry` reads both over plain HTTP from
`base-starter.registry.base-url` — the bucket is public-read, so there is no credential and no S3 client — and
caches the catalog for `catalog-ttl`. A blank base URL means no registry: the catalog is empty.

`GET /starters` and `GET /starters/{starterId}` are tenant-free. They leave out yanked versions, versions whose
`definitionSchemaVersion` this platform cannot read, and starters left with no version.

CI builds the bucket's content with [`tools/business-starters/package-starters.mjs`](../../../tools/business-starters/package-starters.mjs).

## How an install runs

`POST /organizations/{orgKey}/starters/install` with `{ starterId, version, dryRun }`:

1. `InstallStarter` checks design rights, finds the version in the catalog, downloads its bundle and refuses it
   unless it matches the catalog's sha256. The bytes never pass through a client.
2. `BundleReader` reads the zip in memory, under the `base-starter.bundle` limits on the *uncompressed* size,
   and rejects unsafe paths, files the manifest does not list, mismatching `integrity` hashes and an
   unsupported `definitionSchemaVersion`. `ImportBundle` refuses a bundle that is not the requested starter.
3. A starter **replaces** the organization's definitions, so `ImportBundle` refuses (409) an organization for
   which any `InstanceDataProbe` counts data — entity objects (base-entity), workflow instances
   (base-workflow).
4. In one transaction: every `DefinitionImportParticipant` removes its kind's definitions, in reverse kind
   order, then the bundle's files are applied in kind order — entity, state, rule, widget, document, workflow,
   app. Any refusal rolls everything back. A **dry run** is the same transaction rolled back, so it validates
   exactly what an apply would; its report lists definitions to create, update and delete.
5. On apply, the importer replaces the organization's **provenance** — starter id, version and the sha256 of
   each definition's canonical fingerprint — and its installed starter.

The SPI lives in `processpuzzle-core`; base-entity, base-state and base-rule implement the participant today.
`GET /organizations/{orgKey}/starters` lists the installed starter; a definition counts as *customized* when
its current fingerprint no longer hashes to the stored value.

## Contract

`libs/java-shared/api-contracts/src/main/resources/base-starter-api.yaml`. A refused install answers 409, 413 or
422 with the same `ImportReport` a successful one returns; an unknown starter is 404 and an unreadable
registry 503. `POST /organizations/{orgKey}/definitions/import` is the byte-level entry point the install runs
on; it is not offered to users.

## Not yet

Upgrades and the migration script (the manifest carries `migration`, nothing runs it), export, and participants
for widget, document, workflow and app definitions.
