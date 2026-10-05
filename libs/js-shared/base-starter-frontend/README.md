# @processpuzzle/base-starter

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-starter-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_starter_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_starter_frontend)

## Introduction

The Angular half of **Business Starters**: the screen that imports a starter bundle into the current
organization. The backend half, which validates the bundle and runs every feature's importer in one
transaction, is [base-starter-backend](../../java-shared/base-starter-backend/README.md). Design:
[business-starters-design.md](../../../docs/business-starters/business-starters-design.md).

## The import screen

`StarterImportComponent` (`pp-starter-import`), mounted by `BASE_STARTER_ROUTES` at `starters`:

1. Pick a bundle `.zip`.
2. **Preview** runs a dry run — the whole import, rolled back — and shows the `ImportReport`: what would be
   created or updated per definition kind, or why the bundle is refused.
3. **Import** is enabled only after a preview of the *same* file came back `would-apply`.
4. Below, the starters already installed, with how many of their definitions were changed since import.

A refused import is a report, not an error: the backend answers 422 / 413 with the same `ImportReport`
shape, and `StarterService` emits it. Installing from the starter **catalog** is not part of this library;
the catalog lives in processpuzzle-biz and calls the same endpoint.

## Using it

```ts
import { BASE_STARTER_ROUTES } from '@processpuzzle/base-starter';

export const routes: Routes = [{ path: 'design', children: [...BASE_STARTER_ROUTES] }];
```

Copy the translations to `assets/i18n/base_starter` (see the testbed's `project.json`). The endpoints are
read from `STARTER_SERVICE_ROOT`, falling back to `BACKEND_SERVICE_ROOT`.
