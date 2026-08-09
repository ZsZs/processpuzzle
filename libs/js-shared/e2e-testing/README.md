# @processpuzzle/e2e-testing

Descriptor-driven Playwright framework for end-to-end testing any ProcessPuzzle-based application. Given a set of `BaseEntityDescriptor` definitions exposed by the application, this library generates LIST and CRUD test suites automatically — no per-entity boilerplate.

## Why

ProcessPuzzle applications describe their entities through `BaseEntityDescriptor` (see `@processpuzzle/base-entity`). The same metadata that drives the runtime UI — control types, identification field, foreign-key links — is enough to drive e2e coverage. This library reads the descriptors at test time and produces:

- A **LIST suite**: toolbar, filter, new-button, and non-empty rows for each entity.
- A **CRUD suite**: CREATE → READ → UPDATE → DELETE in serial mode, with foreign-key ids propagated between entities in topological order.
- A **RELATIONSHIP suite**: one test per to-many relationship attribute (`RELATED_ENTITIES`, `COMPONENTS`, `EMBEDDED_COMPONENTS`), each asserting what that control type does differently from the other two.

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

The registry derives each entity's base path by walking the router's configuration, and a `loadChildren` branch that has never been entered is not in it — the registry endpoint is a page load of its own. An entity mounted there arrives without a route, and the suites would fall back to guessing `<routePrefix>/<kebab-name>`, which the router answers with `NG04002`. Name its base path yourself; setup warns about every entity it had to leave unrouted, so a missing entry is reported next to its cause rather than three layers away:

```ts
createGlobalSetup({ registryPath: REGISTRY_PATH, routeOverrides: { 'App Definition': '/design/app-definition' } });
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

```ts
// src/tests/entity-relationships.spec.ts
import { defineEntityRelationshipSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityRelationshipSuite({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix });
```

```ts
// src/tests/entity-artifact.spec.ts
import { defineEntityArtifactSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityArtifactSuite({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix });
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

`buildUpdateData` changes every control's value except two: the identification attr (`isLinkToDetails`), which is how the test finds its own row, and `id`. `BaseEntity.id` is `readonly` and the URL a detail form saves to already names it, so an update that renamed an entity would be answered `200` with the id unchanged — and fail on the read-back rather than on the write. Most entities never put `id` on a form, but one with a natural key does: `App Definition` asks the designer for `claims-app` on create, and is thereafter addressed by it. Note that the framework does not yet stop a *user* from typing in such a box on an existing entity; the change is silently discarded on save.

## The relationship suite

A to-many relationship is not a value a form can be filled with, and it is established from the form of an entity that **already exists** — a component's foreign key points back at its parent, and an embedded row has nowhere to be stored until the owner's document does. So these flows are not part of the CRUD suite; they get their own, with their own fixture lifecycle, one test per relationship attribute:

| Control type | Row means | What the test asserts, beyond attaching |
|---|---|---|
| `RELATED_ENTITIES` | a reference to an entity that lives on its own | removing the row detaches only — the target is **still in its own list** |
| `COMPONENTS` | a child persisted through its own endpoint, pointing back through a foreign key | the child is created, read, edited and destroyed **through the owner's form alone**; attaching stamps the owner into its foreign key, and removing the row **destroys** it |
| `EMBEDDED_COMPONENTS` | a child carried inside the owner's payload, edited on a nested route | the same, one step further: the child has no endpoint at all, so its own Save writes the owner's document, the owner's form never saves, the breadcrumb names the chain, and an unsaved owner offers no add button |

**Where a save is effective** is what separates the two containment flows, and each test asserts it: a component's own fields take the child's Save while the reference array takes the owner's, whereas an embedded child's Save writes the containing document and the owner's Save takes no part at all.

Neither containment flow ever addresses the child's own URL — a component belongs to one parent, so its list is only the picker the owner's add button opens, and an embedded child has no list. A component is therefore created by that picker's **New**, and picked on a second visit: `SELECT_OR_CREATE` hands a *selection* back to the form that asked, never a freshly created entity.

The add button of all three controls is `display: none` until the fieldset has focus, so `RelationshipFieldsetPO` focuses before reaching for it. A hidden element is absent from the accessibility tree, which makes an unfocused role query fail with "not found" rather than "not visible".

Describe titles are `[<Entity>] RELATIONSHIP <attrName> (<KIND>)`, so a `E2E_SUITE`-style grep on `RELATIONSHIP` selects the suite the same way `CRUD` and `LIST` do. `maxEmbeddedDepth` (default 2) bounds how far the embedded flow recurses into children that carry children of their own.

Requirements on the application: the row list must render as the generated controls do — a `fieldset` carrying the `<entity>-<attrName>` test id, `<li>` rows whose `<a>` shows the child's identification value, an `Add <Entity Name>` button, and a per-row delete button labelled `Delete related entity reference` / `Delete component` / `Delete embedded component`. A deletion that destroys something is confirmed through `DeleteConfirmationDialog`, whose buttons carry `delete-confirmation-confirm` / `-cancel`.

## The artifact suite

An `ARTIFACT` attribute is the one control whose value does not live in the entity. The form holds a *reference* — `{bucket, objectId, name, mimeType}` — and the bytes it names live in an object store the entity's own endpoint knows nothing about. So it is not a value the generated data can carry, and it gets its own suite for the same reason the relationships do, one test per artifact attribute.

The test walks the whole round trip and asserts at each point that the reference and the object are still the same thing:

| Step | What it proves |
|---|---|
| upload | an object reached the store; the row that appears names it |
| owner's Save, then reload | the reference travels in the **owner's** payload — the row came back from the entity, not from the component's state |
| following the row's link | the URI the store resolves serves back **exactly the bytes uploaded** (length-checked), which is what separates a working store from a form field holding a plausible object id |
| row delete | the delete reaches the store, not just the form: the URI stops serving |
| image vs. text payload | a raster image renders a **thumbnail** — the one place the control needs the store to have *derived* something — and anything else a MIME icon |

The payloads are built in memory by `createPngBuffer` / `createTextBuffer` and handed to `setInputFiles` as bytes, so nothing has to be resolved relative to whichever app runs the suite. The PNG is 320×240, larger than the 200 px thumbnail box in both directions, so the downscale is real.

Both object-store adapters must pass this suite — `processpuzzle-store` over MinIO and the `objectStore` Cloud Function over Firebase Storage — which is what keeps them interchangeable. Where they legitimately differ the suite claims only what both do: a delete asserts the **object** is gone and says nothing about the thumbnail, because MinIO leaves it behind where the Cloud Function cascades to it.

The link is opened with `window.open(…, 'noopener')`, so the new tab has no opener and arrives on the browser *context* rather than as a popup — `ArtifactFieldsetPO.openArtifact` waits on `context.waitForEvent('page')` accordingly.

Describe titles are `[<Entity>] ARTIFACT <attrName>`, so an `E2E_SUITE`-style grep on `ARTIFACT` selects the suite as `CRUD`, `LIST` and `RELATIONSHIP` do.

Requirements on the application: the control must render as the generated one does — a host carrying the `<entity>-<attrName>` test id around a `fieldset.base-entity-form-field`, `<li>` rows whose `<a>` shows the file name, an `Upload file` button revealed by focus opening a selector with a file input, `Artifact name` / `MIME type` placeholders and an `Upload` button, `img.artifact-thumbnail` / `mat-icon.artifact-icon`, and a row delete button labelled `Delete artifact reference` confirmed through `DeleteConfirmationDialog`.

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
| `defineEntityRelationshipSuite(options)` | suite factory | Registers `[entity] RELATIONSHIP <attr> (<KIND>)` for every to-many relationship attribute. |
| `defineEntityArtifactSuite(options)` | suite factory | Registers `[entity] ARTIFACT <attr>` for every `ARTIFACT` attribute — the round trip through the object store. |
| `RouteResolver` | class | Builds list/detail routes, plus `embeddedDetailRoute` for a row nested below its owner's form. Uses `descriptor.route` from the registry when present; otherwise falls back to `${routePrefix}/${kebab(entityName)}`. |
| `EntityListPO`, `EntityFormPO`, `RelationshipFieldsetPO`, `ArtifactFieldsetPO` | classes | Page objects for list views, detail/edit forms, the row list of one relationship attribute, and the fieldset of one artifact attribute. |
| `EntityCrudFixtureManager` | class | Creates the fixtures a test needs and removes them in teardown. `trackFixture` adopts an entity the test created through another entity's form — a component, which has no list to start from. |
| `relationshipTestersFor`, `artifactTestersFor`, `parentReferenceAttrName` | functions | The relationship attributes of a descriptor, its artifact attributes, and a component's foreign key back to a given parent. |
| `createPngBuffer`, `createTextBuffer` | functions | In-memory upload payloads for the artifact suite — no fixture file to resolve on disk. |
| `toTestId`, `attrSelector`, `buttonTestId`, `buttonSelector`, `formControlSelector`, `formControlLocator` | functions | Selector helpers that encode the `data-testid` conventions. |
| `inputAttrs`, `identificationAttr`, `buildCreateData`, `buildUpdateData` | functions | Data-shape helpers driven by descriptors. |
| `resolveDependencyOrder` | function | Topological sort by `FOREIGN_KEY` links. |
| `BaseEntityDescriptor`, `BaseEntityAttrDescriptor`, `FormControlType` | re-exported types | Type-only re-exports from `@processpuzzle/base-entity` for consumer convenience. |

## Testing this library

`nx test e2e-testing` runs Vitest over the parts that are not a browser — descriptor interpretation, selector and route construction, generated test data, the binary fixtures. Those are where a failing assertion means something.

The page objects, the suite factories and the global setup are **excluded from coverage** rather than covered. A page object is correct when its selectors match the DOM the application renders, and only a real run answers that; a test asserting it called `getByRole('button', {name: 'Upload'})` on a mocked `Locator` would pass just as happily when the button says something else. What verifies those files is the generated suites running green against a real application — which is what `nx e2e processpuzzle-testbed-e2e` does in CI. The exclusion list lives in both `vitest.config.ts` and `sonar-project.properties`; keep them in step.

## Notes

- The descriptor types are re-exported as **types only**. `@processpuzzle/base-entity` is an Angular library; evaluating it inside a Node.js / Playwright process fails because the Angular JIT compiler isn't loaded. Internally this library compares `formControlType` against string literals matching the `FormControlType` enum values.
- Currently deferred control types: `ADDITIONAL_PROPERTIES`, `FLEX_BOX` and `LABEL`. They are skipped in both data generation and form interaction.
- The three relationship control types and `ARTIFACT` take no part in data generation or form filling either — neither a relationship nor a stored file is a scalar — but they are covered by the relationship and artifact suites above, and by nothing in the CRUD suite. Keeping `ARTIFACT` out of `fillForm` also keeps the object store off the critical path of a suite that otherwise never touches it.
- Embedded entities (`isEmbedded`) are skipped by the CRUD, LIST and RELATIONSHIP suites as owners: they have no list or detail route of their own. Their own relationships are reached through the owner that carries them, which is what the embedded flow recurses into.
- Foreign-key support depends on the application's `EntityRegistryComponent` serializing `linkedEntityType` (entity name) on `FOREIGN_KEY` attrs. If your serializer omits it, FK resolution will be silently no-op'd.

## License

MIT — see the workspace root.