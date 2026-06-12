# @processpuzzle/e2e-testing

Descriptor-driven Playwright framework for end-to-end testing any ProcessPuzzle-based application. Given a set of `BaseEntityDescriptor` definitions exposed by the application, this library generates LIST and CRUD test suites automatically — no per-entity boilerplate.

## Why

ProcessPuzzle applications describe their entities through `BaseEntityDescriptor` (see `@processpuzzle/base-entity`). The same metadata that drives the runtime UI — control types, identification field, foreign-key links — is enough to drive e2e coverage. This library reads the descriptors at test time and produces:

- A **LIST suite**: toolbar, filter, new-button, and non-empty rows for each entity.
- A **CRUD suite**: CREATE → READ → UPDATE → DELETE in serial mode, with foreign-key ids propagated between entities in topological order.

The consuming project provides three things: a Playwright config, a route prefix, and an endpoint that serves the descriptor registry as JSON. Everything else is generic.

## Install

```bash
npm install --save-dev @processpuzzle/e2e-testing
```

Peer dependencies (must be installed by the consuming project):

- `@playwright/test`
- `@processpuzzle/base-entity` (used only for descriptor TypeScript types)

## Application requirements

The application under test must expose a `/entity-registry` route (or any path of your choosing) that returns the descriptor list wrapped in a `<pre>` element as JSON. The standard ProcessPuzzle `EntityRegistryComponent` from `@processpuzzle/base-entity` does this for you. The expected wire format per entry:

```jsonc
{
  "entityName": "Test Entity",
  "entityTitle": "Test Entities",
  "attrDescriptors": [
    { "attrName": "name", "formControlType": "TEXT_BOX", "isLinkToDetails": true, "visible": true, "required": true },
    { "attrName": "createdOn", "formControlType": "DATE", "visible": true }
    // ...
  ]
}
```

`formControlType` values must match the string values of `FormControlType` in `@processpuzzle/base-entity` (`TEXT_BOX`, `TEXTAREA`, `CHECKBOX`, `DATE`, `DROPDOWN`, `TAGS`, `FOREIGN_KEY`, …).

The list/detail views must follow the `data-testid` conventions encoded in `selector.builder.ts`:

- Buttons: `<entity-test-id>-new`, `-save`, `-edit`, `-delete`, `-cancel`
- Filter input: `<entity-test-id>-filter`
- Form controls: `<entity-test-id>-<attrName>`

where `<entity-test-id>` is the entity name camel-cased (`Test Entity Component` → `testEntityComponent`).

## Quick start

### 1. Playwright config

```ts
// playwright.config.ts
import { defineConfig } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';

export const testConfig = { routePrefix: '/base-entity/samples' };

export default defineConfig({
  ...nxE2EPreset(__filename, { testDir: './src' }),
  globalSetup: './src/support/global-setup.ts',
  use: { baseURL: 'http://localhost:4200' },
  webServer: { command: 'npx nx run my-app:serve', reuseExistingServer: true },
});
```

### 2. Global setup — fetches and caches the descriptor registry

```ts
// src/support/global-setup.ts
import * as path from 'path';
import { createGlobalSetup } from '@processpuzzle/e2e-testing';

export const REGISTRY_PATH = path.join(__dirname, '../../tmp/entity-registry.json');

export default createGlobalSetup({ registryPath: REGISTRY_PATH });
```

Override the default `/entity-registry` route if your app uses a different path:

```ts
createGlobalSetup({ registryPath: REGISTRY_PATH, registryUrl: '/my-registry' });
```

### 3. Spec files — one line each

```ts
// src/tests/entity-list.spec.ts
import { defineEntityListSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityListSuite({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix });
```

```ts
// src/tests/entity-crud.spec.ts
import { defineEntityCrudSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityCrudSuite({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix, expectTimeoutMs: 15_000 });
```

Run with `nx e2e <your-e2e-project>`. The number of generated test cases scales automatically with the number of entities exposed by your application.

`expectTimeoutMs` is optional. When omitted, form control assertions use the Playwright configured expect timeout.

## How it works

```
globalSetup ──► GET <baseURL>/entity-registry
                  │
                  └─► topological sort by FOREIGN_KEY links
                  │
                  └─► writes JSON to registryPath

spec import time ──► reads registryPath
                  │
                  └─► loops over descriptors
                  │
                  └─► test.describe(`[${entityName}] LIST/CRUD`, …)
```

The CRUD suite runs in **serial** mode and records each created entity's id in a shared map (`createdIds`). Later entities whose descriptor contains a `FOREIGN_KEY` attr referencing an earlier entity get the recorded id substituted in `buildCreateData`. This is why dependency ordering matters.

## Custom tests with the page objects

If a particular entity needs scenarios beyond the generic suites, instantiate the page objects directly:

```ts
import { test } from '@playwright/test';
import { EntityFormPO, EntityListPO, RouteResolver } from '@processpuzzle/e2e-testing';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';

const routes = new RouteResolver('/base-entity/samples');

test('custom flow', async ({ page }) => {
  const descriptor: BaseEntityDescriptor = /* … */;
  const list = new EntityListPO(page, descriptor, routes);
  const form = new EntityFormPO(page, descriptor, routes, { expectTimeoutMs: 15_000 });

  await list.navigateTo();
  await list.clickNew();
  await form.fillForm({ name: 'Custom' });
  await form.save();
  await list.assertInList('Custom');
});
```

## Public API

| Symbol | Kind | Purpose |
|---|---|---|
| `createGlobalSetup(options)` | factory | Returns a Playwright `globalSetup` that loads & caches the descriptor registry. |
| `defineEntityListSuite(options)` | suite factory | Registers `[entity] LIST › renders toolbar and rows` for every descriptor. |
| `defineEntityCrudSuite(options)` | suite factory | Registers `[entity] CRUD › CREATE/READ/UPDATE/DELETE` for every descriptor, in serial mode. |
| `RouteResolver` | class | Builds list/detail routes from a configurable `routePrefix`. |
| `EntityListPO`, `EntityFormPO` | classes | Page objects for list and detail/edit views. |
| `toTestId`, `attrSelector`, `buttonTestId`, `buttonSelector`, `formControlSelector`, `formControlLocator` | functions | Selector helpers that encode the `data-testid` conventions. |
| `inputAttrs`, `identificationAttr`, `buildCreateData`, `buildUpdateData` | functions | Data-shape helpers driven by descriptors. |
| `resolveDependencyOrder` | function | Topological sort by `FOREIGN_KEY` links. |
| `BaseEntityDescriptor`, `BaseEntityAttrDescriptor`, `FormControlType` | re-exported types | Type-only re-exports from `@processpuzzle/base-entity` for consumer convenience. |

## Notes

- The descriptor types are re-exported as **types only**. `@processpuzzle/base-entity` is an Angular library; evaluating it inside a Node.js / Playwright process fails because the Angular JIT compiler isn't loaded. Internally this library compares `formControlType` against string literals matching the `FormControlType` enum values.
- Currently deferred control types: `ARTIFACT`, `LOOKUP`, `COMPONENTS`. They are skipped in both data generation and form interaction.
- Foreign-key support depends on the application's `EntityRegistryComponent` serializing `linkedEntityType` (entity name) on `FOREIGN_KEY` attrs. If your serializer omits it, FK resolution will be silently no-op'd.

## License

MIT — see the workspace root.
