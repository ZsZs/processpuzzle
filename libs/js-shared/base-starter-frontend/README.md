# @processpuzzle/base-starter

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-starter-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_starter_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_starter_frontend)

## Introduction

The Angular half of **Business Starters**: the screen that installs a starter from the catalog into the
current organization. The backend half, which reads the registry, verifies the bundle and replaces the
organization's definitions through every feature's importer in one transaction, is
[base-starter-backend](../../java-shared/base-starter-backend/README.md). Design:
[business-starters-design.md](../../../docs/business-starters/business-starters-design.md).

## The install screen

`StarterImportComponent` (`pp-starter-import`), mounted by `BASE_STARTER_ROUTES` at `starters`:

1. Pick a starter from the catalog, and a version (the newest by default).
2. **Preview** runs a dry run — the whole install, rolled back — and shows the `ImportReport`: what would be
   created, updated and deleted per definition kind, or why the install is refused.
3. **Install** is enabled only after a preview of the *same* starter and version came back `would-apply`.
4. Below, the starter already installed, with how many of its definitions were changed since.

## Upgrading

When the platform has rolled out a newer version of an installed starter, its row in the installed-starters table
shows **Upgrade to <version>**:

1. **Preview** plans the upgrade (`POST …/starters/{id}/upgrade` with `dryRun`) and shows an `UpgradeReport`:
   what is *added* or *replaced* (untouched here), *removed* (dropped by the starter and untouched), and what is **kept** —
   changed here (*customized*, the organization's version stays), deleted here (not brought back), or still in use. Kinds
   the starter brings that cannot be upgraded yet are named and left as they are.
2. **Apply upgrade** is enabled only after a preview of that starter came back `would-apply`, and applies exactly the
   version the preview showed. One published version at a time: when the organization is several versions behind, the
   row offers the next one again afterwards.
3. The upgrade writes **drafts**; it releases nothing. Review them in the designer and release when it suits you.

A starter replaces the organization's definitions, so the backend refuses it once the organization holds
entity objects or workflow instances. A refused install is a report, not an error: the backend answers
409 / 413 / 422 with the same `ImportReport` shape, and `StarterService` emits it.

## Using it

```ts
import { BASE_STARTER_ROUTES } from '@processpuzzle/base-starter';

export const routes: Routes = [{ path: 'design', children: [...BASE_STARTER_ROUTES] }];
```

Copy the translations to `assets/i18n/base_starter` (see the testbed's `project.json`). The organization's
endpoints are read from `STARTER_SERVICE_ROOT`, falling back to `BACKEND_SERVICE_ROOT`; the tenant-free
catalog from the same root with its `/organizations/<orgKey>` tail removed.
