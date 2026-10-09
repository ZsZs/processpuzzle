# External Frontend Components for ProcessPuzzle

## Problem

With 100% declarative app development in ProcessPuzzle (PP), customers still need a way to use their own compiled frontend components, developed outside of PP. On the backend this is easy, because third-party REST services can be reached at any time. On the frontend, a component has to be loaded and integrated into the running app.

Two sources of components are needed:

- Components built by the customers' own developers
- Components from third parties (marketplace)

## Core Idea

The declarative side needs a **contract**, not a specific technology. If a widget is described as "entry point + config schema + events + permissions", the metadata does not care how the widget was built.

External widgets become one more kind of entry in the generic widget host (the same host that embeds widgets like `mat-card-grid` and feeds them config at compile time or runtime).

## Loading Options

| Option | How it works | Strengths | Costs |
|---|---|---|---|
| **Web Components (custom elements)** | Customer ships a JS bundle (Angular Elements or any framework). PP loads it by URL and renders `<their-tag>`. Config goes in as properties, results come back as `CustomEvent`s. | Framework-agnostic, survives Angular upgrades | Same-origin JS needs trust or review |
| **Module Federation (Native Federation)** | Angular-built remote loaded at runtime, shared dependencies | Real Angular runtime, tight integration | Version coupling with PP's Angular version |
| **iframe + postMessage** | Widget runs in a sandboxed iframe on its own origin | Strongest isolation, any stack | Poor visual integration (sizing, theming, focus) |
| **Compile-time package** | Component installed from npm into a customer-specific build (the "Custom" app type) | Full control for the customer | Customer owns the build |

**Recommendation:** start with custom elements as the single runtime path, keep the iframe as the isolation fallback, and add Module Federation only when a customer needs the Angular runtime.

## Trust Tiers

| Tier | Source | Loading | Isolation |
|---|---|---|---|
| **own** | Customer's own developers | Custom element, or compile-time package | Same origin, trusted by the customer's admin |
| **reviewed** | Marketplace, vetted by PP | Custom element from a PP-controlled CDN, pinned version and integrity hash | Same origin, only after review and signing |
| **unreviewed** | Third party, not vetted | Sandboxed iframe, postMessage only | Separate origin |

- The manifest declares the tier, and the host picks the loader.
- Marketplace components declare the **permissions** they need. The SDK enforces them, and the tenant admin approves them on install.
- Trust is per tenant: a customer can restrict their tenant to "own + reviewed" only.
- Marketplace needs versioning and compatibility ranges against the widget-contract version, a review and signing process, and a revocation path to disable a bad component across tenants.

## Widget Contract

A widget interacts with PP only through:

- **Manifest:** entry URL, tag, version, tier, config schema
- **Context SDK:** locale, theme, session (token proxy, never raw credentials), entity/workflow access limited by permissions
- **Events:** a defined set that the host maps to PP actions
- **Theming:** CSS custom properties from the PP theme (these pierce the shadow DOM)

## Manifest Sketch

```yaml
apiVersion: processpuzzle.io/widget/v1
kind: WidgetManifest
metadata:
  id: acme.race-timer
  version: 1.2.0
  vendor: acme
  tier: reviewed            # own | reviewed | unreviewed
  contract: ">=1.0 <2.0"    # PP widget-contract range it supports

runtime:
  type: custom-element      # custom-element | federated | iframe
  entry: https://cdn.example.com/race-timer/1.2.0/main.js
  integrity: sha384-...     # required for reviewed/unreviewed
  tag: acme-race-timer      # custom-element only
  # iframe only: sandbox: [allow-scripts], minHeight: 300

config:                     # JSON Schema; PP validates and can generate the editor
  type: object
  required: [raceId]
  properties:
    raceId: { type: string }
    showLaps: { type: boolean, default: true }

events:                     # what the widget may emit; manifest provides default actions
  - name: lap-recorded
    payload: { type: object, properties: { boat: { type: string }, ms: { type: integer } } }
    default: { action: entity.create, entity: Lap }
  - name: navigate
    payload: { type: object, properties: { route: { type: string } } }
    default: { action: router.navigate, target: "{{event.route}}" }

permissions:                # approved by the tenant admin on install
  entities: [{ name: Race, access: read }, { name: Lap, access: write }]
  workflows: [{ name: finish-race, access: invoke }]
  navigation: true

context:                    # what the host injects via the SDK
  - locale
  - theme
  - session

theming:
  cssVariables: [--pp-primary, --pp-surface, --pp-radius]
```

## Route Usage Sketch

```yaml
route: /races/:id/timing
widget:
  ref: acme.race-timer@^1.2          # id@version-range
  config: { raceId: "{{route.id}}" } # templated config
  on:
    lap-recorded: { action: workflow.invoke, workflow: record-lap }   # overrides the manifest default
    navigate: ~                                                       # disables the default
```

## Event Resolution Rules

- If the route defines no entry for an event, the manifest default applies.
- If the route defines one, it replaces the default entirely (no merging).
- An explicit `~` (null) disables the event.
- An event with no default and no route entry is ignored and logged in dev mode.
- Overrides cannot escalate: an action that is not covered by the manifest's declared permissions is rejected at load time.

## Design Choices

- **Config as JSON Schema** gives validation and an auto-generated config UI. Built-in widgets (e.g. `mat-card-grid`) can get manifests too, so every widget goes through one path.
- **Permissions and events** are the only ways a widget touches PP, which keeps the marketplace tier safe.
- **`ref: id@range`** allows version pinning per route or tenant and central revocation.
- **Templated config** (`{{route.id}}`) keeps the compile-time/runtime config distinction inside the host.

## Security Notes

- Runtime-loaded customer JS runs in the PP origin, so use a per-tenant CSP allowlist.
- Only admin-registered manifests may load.
- Require integrity hashes for reviewed and unreviewed tiers.
- Anything less trusted goes into the sandboxed iframe.

## Decisions So Far

- Manifest-based external widgets with the three trust tiers above
- Both customer-built and marketplace components are in scope
- Event-to-action mapping: defaults in the manifest, overridable in the route
- Slots (widgets hosting child widgets) are out of scope for now

## Suggested Implementation Order

1. Contract and manifest model (config schema, events, context SDK), plus the event resolver in the widget host, with built-in widgets as the first manifest consumers
2. Custom element loader for the "own" tier
3. iframe loader as the isolation path
4. Signing, permissions UI, and the marketplace on top
