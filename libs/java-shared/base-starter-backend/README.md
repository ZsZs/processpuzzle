# base-starter-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-starter-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_starter_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_starter_backend)

## Introduction

`base-starter-backend` imports **Business Starter** bundles into one organization. A bundle is a zip with a
`manifest.yaml` at its root and definition files in each feature's own seed YAML format — the same files the
classpath seed loaders read. It is a Spring Modulith application module (`starter`) that depends on no feature
library. Design: [business-starters-design.md](../../../docs/business-starters/business-starters-design.md).

## How an import runs

1. `BundleReader` reads the zip in memory, under the `base-starter.bundle` limits on the *uncompressed* size,
   and rejects unsafe paths, files the manifest does not list, mismatching `integrity` hashes and an
   unsupported `definitionSchemaVersion`.
2. `ImportBundle` maps each `contents` group to a definition kind and finds the
   `DefinitionImportParticipant` bean that imports it. The SPI lives in `processpuzzle-core`; each feature
   implements it for its own kind (base-entity, base-state and base-rule do so today).
3. The participants run in kind order — entity, state, rule, widget, document, workflow, app — inside one
   transaction. Any refusal rolls everything back. A **dry run** is the same transaction rolled back, so it
   validates exactly what an apply would.
4. On apply, the importer records **provenance** per definition — starter id, version and the sha256 of the
   participant's canonical fingerprint — in its own table, and the installed starter.

`GET /organizations/{orgKey}/starters` lists installed starters; a definition counts as *customized* when its
current fingerprint no longer hashes to the stored value.

## Contract

`libs/java-shared/api-contracts/src/main/resources/base-starter-api.yaml`. A refused import answers 422 (or 413
when over a size limit) with the same `ImportReport` a successful one returns.

## Not yet

Export, conflict strategies, `requires` resolution (a required starter must already be installed), seed data,
and participants for widget, document, workflow and app definitions.
