# @processpuzzle/base-release

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-release-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_release_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_release_frontend)

## Introduction

The Angular half of the platform **definition release**: a designer edits drafts, the running application
reads only what was released, and one release makes every pending draft live at once, or none. The backend
half, which plans and records releases, is [base-release-backend](../../java-shared/base-release-backend/README.md).
Design: [definition-release.md](../../../docs/definition-release.md).

## Status

Scaffold. The library is configured like its siblings (build, test, lint, publishing, translation scope) and
is ready to receive the release-specific components. Until they move here, the release panel still lives in
`@processpuzzle/design`.

## Using it

```ts
import { BASE_RELEASE_ROUTES, BASE_RELEASE_TRANSLATION_SOURCE } from '@processpuzzle/base-release';

providers: [{ provide: TRANSLATION_SOURCE_REGISTRY, useValue: BASE_RELEASE_TRANSLATION_SOURCE, multi: true }];

export const routes: Routes = [{ path: 'release', children: [...BASE_RELEASE_ROUTES] }];
```

Copy the translations to `assets/i18n/base_release` (see the testbed's `project.json`). The backend root is
read from `RELEASE_SERVICE_ROOT`, falling back to `BACKEND_SERVICE_ROOT`.
