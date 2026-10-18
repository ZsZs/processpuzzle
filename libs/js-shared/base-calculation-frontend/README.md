# @processpuzzle/base-calculation

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-calculation-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_calculation_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_calculation_frontend)

## Introduction

The Angular half of **base-calculation**: authoring declarative calculations — YAML with DMN semantics, evaluated
with FEEL — and inspecting their runs. The backend half, which validates, evaluates and runs them, is
[base-calculation-backend](../../java-shared/base-calculation-backend/README.md); the definition format is
described there.

Nothing is evaluated in the browser: Validate, Evaluate and Run call the backend, so a preview can never differ
from a run.

## Screens

| Screen | What it does |
| --- | --- |
| List / Details | the generic CRUD of a `Calculation Definition`; the form edits the source, everything else is read from it |
| **Source** tab | a CodeMirror editor for the YAML; Validate lists the errors, or the decisions in evaluation order with what each requires |
| **Decision tables** tab | a grid per table — hit policy, input columns, output, rules added, removed and reordered; every edit rewrites that table in the YAML document and keeps the rest, comments included (`yaml`, ISC) |
| **Evaluate** tab | a field per parameter — an entity parameter picks from its type's objects, titled by the attribute that titles them (a plain id field beyond 100 objects); *Evaluate draft* computes and stores nothing, *Run released* writes the output and keeps the run; both show values and the trace |
| Calculation runs | every run, filterable by calculation and subject, with values, trace, the source snapshot it ran, and Freeze |

## Using it

```ts
import {
  BASE_CALCULATION_ENTITY_FACADES, BASE_CALCULATION_FACADE_PROVIDERS, BASE_CALCULATION_ROUTES,
  BASE_CALCULATION_TRANSLATION_SOURCE, CALCULATION_RUNS_ROUTES,
} from '@processpuzzle/base-calculation';

providers: [
  ...BASE_CALCULATION_FACADE_PROVIDERS,
  { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { ...BASE_CALCULATION_ENTITY_FACADES /* , ...others */ } },
  { provide: TRANSLATION_SOURCE_REGISTRY, useValue: BASE_CALCULATION_TRANSLATION_SOURCE, multi: true },
];

const routes: Routes = [{ path: 'calculations', children: [...BASE_CALCULATION_ROUTES, ...CALCULATION_RUNS_ROUTES] }];
```

The service root is `CALCULATION_SERVICE_ROOT`, falling back to `BACKEND_SERVICE_ROOT`. Copy
`src/assets/i18n/base_calculation` to the application's `assets/i18n/base_calculation` (see the testbed's
`project.json`); the scope is registered on both route branches.

The YAML helpers behind the table editor — `readTables`, `writeTable`, `removeTable` — and the
`CalculationApiService` (validate, evaluate, run, runs, trace, freeze) are exported for a host that builds its own
screens.
