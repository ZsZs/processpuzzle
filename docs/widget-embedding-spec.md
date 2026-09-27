# Widget Embedding — Technical Spec

**Scope:** `base-widget-frontend` (the contract, registry and host), and its two aggregators —
`base-app-frontend` (routes and shell regions) and `base-document-frontend` (WIDGET blocks and inline embeds).

Status: implemented. This spec replaces an earlier draft that proposed a single `config` input, a
`slot`/`role` pair on the instance and a `route-widget-layout` entity; see [§7](#7-decisions-and-rejected-alternatives)
for why each was dropped.

---

## 1. Goals

- **One interface per widget.** Whoever places a widget — a designer in an `AppDefinition`, an author in a
  document, a developer in a template — configures it the same way and gets the same behavior.
- **One host.** Every aggregator renders a `WidgetInstance` through the same component, with the same
  unknown-type fallback, the same prop filtering and the same output wiring.
- **Self-describing widgets.** A widget's description (props schema, ports, palette metadata) lives next to
  its component and is checked against it at test time, so the catalogue cannot promise what the component
  does not do.
- **Additive.** A new widget type is one registration in its own library; nothing central changes.

## 2. The widget contract

A component is a widget when it satisfies these rules — `widget-contract.spec.ts` enforces them for every
widget `provideBaseWidgets()` registers:

1. **Configuration arrives through signal `input()`s**, one per `propsSchema` property, named identically.
   No `@Input()` decorators, no aliases, no second name for the same value.
2. **The set of inputs equals the set of `propsSchema.properties`.** An undescribed input cannot be
   configured by a designer; a described prop without an input is silently dropped.
3. **Events leave through `output()`s**, and every output is declared as an `outputPort` of the same name.
4. **Environment comes through DI, configuration through inputs.** Transloco, `RUNTIME_CONFIGURATION`, the
   application-property store or the router are the environment — a widget may inject them. Anything a
   designer should be able to vary per placement is an input.
5. **Sensible defaults.** An input is `input.required()` only when the widget cannot render without it; the
   schema lists exactly those under `required`. A widget placed with no props must not throw, except for
   its required props.
6. **Selector prefix `pp-`.** Registry keys are semantic (`cards-grid`), selectors are `pp-<key>` where
   practical.

Props are *spread* onto inputs rather than passed as one `config` object: `inputBindings` bind one prop to
one port, a widget stays usable directly in a template (`<pp-copyright [text]="…" />`), and signal inputs
change-detect per value.

## 3. Registration

```typescript
export interface WidgetRegistration {
  type: string;                 // registry key == WidgetInstance.type == WidgetDefinition.key
  component: Type<unknown>;
  definition: WidgetDescription; // name, translocoId, description, category, icon, propsSchema, inputPorts, outputPorts
}

provideWidget(registration: WidgetRegistration): Provider[];
WIDGET_REGISTRY: InjectionToken<ReadonlyMap<string, WidgetRegistration>>;
```

Each widget folder has a `<name>.widget.ts` exporting its key constant and its `WidgetRegistration`.
`base-widget.providers.ts` wraps each in a `provide<Name>Widget()` and `provideBaseWidgets()` registers them
all. Registrations merge within one injector (`multi`) and across injectors (`@Optional() @SkipSelf()`),
so an aggregator — base-document's future `document-viewer` — adds its own without replacing these.

`WidgetDescription` is the frontend twin of the `WidgetDefinition` resource. The backend seed
(`base-widget-backend/.../default-widgets/processpuzzle-testbed-widgets.yaml`) must carry the same entries;
`widget-contract.spec.ts` compares them field by field. The YAML is still what a tenant's catalogue is seeded
from — the registration is what keeps it honest.

## 4. The host

```html
<pp-widget-host [widget]="instance" [bindingResolver]="resolve" (portEmit)="onPort($event)" />
```

`WidgetHostComponent` (`base-widget-frontend`) renders one placement:

- looks `widget.type` up in `WIDGET_REGISTRY`; an unregistered type renders a placeholder naming the type
  (`data-testid="unregistered-<id>"`) instead of throwing;
- resolves inputs with `resolveWidgetInputs()`: `props`, overlaid by each `inputBindings` entry resolved
  through `bindingResolver`, filtered to the inputs the component declares (a stale prop is dropped with a
  console warning, never a runtime error);
- binds every output named in `outputBindings` and re-emits it as `portEmit: { widgetId, port, value }`;
- creates the component through `ViewContainerRef.createComponent`, re-creating it only when the type or the
  set of bound names changes — edits to values flow through `setInput`.

The Tiptap node view of base-document cannot use a template, so it calls the same two functions
(`resolveWidgetInputs`, `widgetOutputBindings`) around `createComponent`. There is no third resolution rule.

Placement stays the container's concern: `REFERENCED` instances are skipped by the container's list (base-app's
`WidgetListComponent`, base-document's editor) and rendered where a `childIds` entry or a `widgetEmbed` node
points at them.

## 5. Where instances come from

There is no separate layout entity. An `AppDefinition` already holds `routes[].widgets` (routes of kind
`WIDGETS`) and `regions[].widgets` (header / footer); a document holds WIDGET blocks. A compile-time
application is a const `AppDefinition`, a run-time one is fetched — the shell cannot tell the difference.

## 5a. The application context

Chrome widgets that show the application itself — `app-title`, `app-logo`, `nav-menu` — read its name, logo
and navigation from `APPLICATION_CONTEXT` (`base-widget-frontend`), which `AppShellComponent` provides from
the `AppDefinition` it renders. Their props override it, which is how the same widgets work in a document
placed outside any application. This is rule 4 of §2: the application is environment, not configuration.

So the shell header has no built-in brand block: it is widgets only, and the seeded applications place
`app-logo`, `app-title` and `nav-menu` there. On a handset layout the shell renders the nav region nowhere —
neither as a sidenav nor as a top-nav row — and `nav-menu` (visible on small screens by default) stands in.

## 6. Validation

- **Design time:** the widget-instance form edits `props` with `WidgetPropsControlComponent` — a base-entity
  `CUSTOM` control that builds a nested form from the selected type's registered `propsSchema`
  (`propsSchemaToDescriptors`) and rebuilds it when the type changes. `format: 'artifact'` maps to the
  ARTIFACT control (an object-store reference, as `app-logo`'s `logo`). An unregistered or undescribed type
  falls back to the open key/value editor; arrays of objects are not yet editable as rows.
- **Render time:** `resolveWidgetInputs()` drops props the component does not declare.
- **Build time:** `widget-contract.spec.ts` — inputs ⇔ schema, outputs ⇔ output ports, registration ⇔ seed YAML.

## 7. Decisions and rejected alternatives

| Draft proposal | Decision |
| --- | --- |
| One `config` input | Rejected — breaks per-prop `inputBindings` and template use; see §2. |
| `slot` on the instance | Rejected — the container (region type, route, document block) is the slot. |
| `role` in the registry | Rejected — `WidgetDefinition.category` already classifies widgets for the palette. |
| `WidgetConfigSource` + `route-widget-layout` entity | Rejected — `AppDefinition` is that source, static or remote. |
| Zod schemas | Rejected — `propsSchema` (JSON Schema) is already the contract. |
| Lazy `loadComponent` | Deferred until the widget count warrants it. |
| `PageContentProvider` | Deferred — no consumer yet. |
