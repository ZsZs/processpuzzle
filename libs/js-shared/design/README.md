# @processpuzzle/design

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-design.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_design&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_design)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fdesign?style=flat)](https://www.npmjs.com/package/@processpuzzle/design)

## Introduction

`@processpuzzle/design` is the **Design-time module** of the ProcessPuzzle Low-Code platform. It provides the Angular entry point through which end-users author the artifacts that make up a business application: **entities**, **rules**, **states**, **workflows**, and **desktops**. The library ships a routable landing page, child routes for each building block, and a route-awareness service that lets host applications react to the user entering or leaving the `/design` area.

It sits alongside the corresponding runtime libraries (`base-entity-frontend`, `base-rule-frontend`, `base-state-frontend`, `base-desktop-frontend`, `base-workflow-frontend`) and composes their routes into a single design experience.

## Features

- **Design landing page** (`DesignContentComponent`) — a card grid, powered by `MatCardsGridComponent` from `@processpuzzle/widgets`, that links to each design building block.
- **Aggregated routing** (`DESIGN_ROUTES`) — a `Routes` array to be mounted under `/design` in the host application; automatically pulls in the child routes exposed by sibling design libraries (currently `BASE_RULE_ROUTES`).
- **Route awareness** (`DesignRouteService`) — a root-provided Angular service exposing an `isDesignRoute` signal so surrounding UI (menus, breadcrumbs, toolbars) can adapt when the user is inside `/design`.
- **i18n scoped translations** — the landing page registers a Transloco scope (`design`), so card titles, subtitles, content, and action captions are fully localizable.

## Public API

| Export                 | Kind                  | Purpose                                                                                    |
|------------------------|-----------------------|--------------------------------------------------------------------------------------------|
| `DESIGN_LIBRARY`       | `const string`        | Library identifier (`'@processpuzzle/design'`) — useful for logging and diagnostics.        |
| `DESIGN_ROUTES`        | `Routes`              | Angular route array to mount under `/design`; includes the landing page and child routes. |
| `DesignContentComponent` | `Component`         | Standalone card-grid landing page shown at `/design`.                                     |
| `DesignRouteService`   | `@Injectable` (root)  | Exposes the `isDesignRoute: Signal<boolean>` for route-aware UI.                          |

## Setup and Usage

### Installation

```bash
npm install @processpuzzle/design
```

### Mounting the routes

In the host application's route configuration, lazy-load the design area under `/design`:

```typescript
import { Routes } from '@angular/router';

export const APP_ROUTES: Routes = [
  {
    path: 'design',
    loadChildren: () => import('@processpuzzle/design').then((m) => m.DESIGN_ROUTES),
  },
];
```

The path segment **must** be `design` — `DesignRouteService` matches URLs starting with `/design`.

### Using the route-awareness signal

```typescript
import { Component, inject } from '@angular/core';
import { DesignRouteService } from '@processpuzzle/design';

@Component({ /* ... */ })
export class ToolbarComponent {
  private readonly designRoute = inject(DesignRouteService);
  readonly isDesignRoute = this.designRoute.isDesignRoute;
}
```

Then in a template:

```html
@if (isDesignRoute()) {
  <pp-design-toolbar />
}
```

## Technical Details

### Dependencies

Runtime peer dependencies (see `package.json`):

- `@angular/common`, `@angular/core`, `@angular/router` — `~22.0.5`
- `@jsverse/transloco` — `8.4.0`
- `@processpuzzle/widgets` — `^0.8.0`
- `rxjs` — `~7.8.2`

### Architecture Notes

- **Standalone components** — no `NgModule`; everything is `standalone: true` and consumed via imports.
- **Signals over observables** — `DesignRouteService` exposes state as a `Signal<boolean>` rather than an `Observable`, aligning with Angular 21's signal-first direction. The router subscription uses `takeUntilDestroyed()` for automatic cleanup.
- **Composable routes** — `DESIGN_ROUTES` spreads child libraries' route arrays, so adding a new design building block only requires extending the array.
- **Scoped i18n** — the `design` Transloco scope is provided at the component level, keeping translation keys isolated from the host application.

### Testing

- Vitest with `happy-dom` (see `vitest.config.ts`).
- Coverage collected via `@vitest/coverage-v8`; run with `pnpm nx test design --configuration=ci --coverage`.

### Build

- Packaged with `ng-packagr` via `@nx/angular:package`.
- Build with `pnpm nx build design --configuration=production`.

## Status

Early-stage — the current API surface covers the design landing page, aggregated routing, and route-awareness. Additional building blocks (entities, states, workflows, desktops) will be linked in as their route arrays become available.
