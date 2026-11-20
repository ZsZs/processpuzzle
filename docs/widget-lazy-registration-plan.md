# Lazy widget registration – plan

2026-11-20 · status: phases 1–3 done 2026-11-20 (see §8), phase 4 not started · follows [Widget Embedding](widget-embedding-spec.md) §7, where
"Lazy `loadComponent`" was deferred "until the widget count warrants it".

## 1. Why now

A widget registration holds the component *class*, so registering a widget links its whole dependency tree into the
application's **initial bundle**, whether or not a page ever places it. The `calendar` widget is the first one heavy enough
to matter: FullCalendar and its time-zone polyfill add **about 410 kB raw (about 90 kB transferred)**.

| Testbed production build | Initial, raw | Initial, transferred | Error budget |
| --- | --- | --- | --- |
| without the calendar widget registered | 9.86 MB | 2.10 MB | 10 MB |
| with it registered | 10.27 MB | 2.19 MB | 10 MB → **11 MB** (raised 2026-11-20) |

The budget was raised as a stopgap; it buys room for one more widget of this size. The map and chart style widgets that
are on the horizon would hit the same wall. The structural fix is for the application to pay for a widget when a page
first places it.

## 2. Where the component class is needed today

`WidgetRegistration` is `{ type, component: Type<unknown>, definition }`. Who reads what:

| Consumer | Reads | Needs the class? |
| --- | --- | --- |
| `WidgetHostComponent` (`base-widget-frontend/src/widget-host`) | `reflectComponentType` via `resolveWidgetInputs` and `widgetOutputBindings`, then `createComponent` | **yes**, three synchronous uses in `mount()` and the `inputs` computed |
| `widget-embed-node.ts` (base-document, the Tiptap node view) | `widgetOutputBindings`, `createComponent`, `resolveWidgetInputs(componentRef.componentType)` | **yes** |
| `widget-props-control`, `app-widget.facade` (designer forms), `document-editor` (palette, props of a block) | `definition` only | no, and they must stay synchronous |
| the registry factory in `widget-registry.token.ts` | `existing.component !== registration.component` to detect a clash | needs a rework |
| contract specs (`widget-contract.spec.ts`, and those of chat, calendar, content-book) | `reflectComponentType(registration.component)` | need to await |

Everything a designer sees is in `definition`, which is small JSON and stays eager. Only *rendering* needs the class.

## 3. Design

**Registration.** Keep `definition` eager. Replace `component` with exactly one of two forms:

```ts
type WidgetRegistration = { type: string; definition: WidgetDescription } &
  ({ component: Type<unknown>; loadComponent?: never } | { component?: never; loadComponent: () => Promise<Type<unknown>> });
```

Small widgets keep `component`; nothing is migrated in bulk. A heavy one writes
`loadComponent: () => import('./calendar.component').then((m) => m.CalendarComponent)`.

**One loader, memoized.** `loadWidgetComponent(registration): Promise<Type>` caches the promise per registration (a
`WeakMap`), resolves immediately for an eager one, and does **not** cache a rejection, so a failed chunk fetch can be retried.
A companion `loadedWidgetComponent(registration): Type | undefined` answers synchronously once loaded, so a second placement
of the same type renders without a pause.

**Host.** `component` becomes a signal with three states, loading, ready and failed, driven by the registration:

- the `inputs` computed and `mount()` wait for `ready`;
- the host renders nothing while loading (it is `display: contents`; add `aria-busy` on the outlet), and the same
  "unregistered" fallback, with a different `title`, when loading failed;
- a load that finishes after the widget type changed, or after the host was destroyed, is discarded.

**Tiptap node view.** The `await` goes before `createComponent`; a teardown token is checked after it, since the node
can be destroyed or re-targeted meanwhile; a rejection goes to the existing `renderProblem`.

**Registry factory.** Compare by `type` plus the component, or the loader function, whichever the registration has.

**Preloading (optional, last).** A shell that renders an `AppDefinition` knows its widget types before it draws them. A
`preloadWidgets(types)` helper would start the loads when the route activates, so the chunk is usually there by first paint.

**Bundler caveat.** The chunk is only split off if nothing in the initial graph imports the component statically.
`calendar.widget.ts` must use `import()`; `public-api.ts` re-exports `CalendarComponent`, which is dropped only if tree
shaking removes it (the package declares `sideEffects: false`). **Measure; do not assume.** If the re-export keeps the
component in the initial chunk, move the component's export to a secondary entry point.

## 4. Phases

| Phase | Scope | Done when |
| --- | --- | --- |
| 1 Widgets library | The registration type, the memoized loader, the async host, the registry clash rule; a helper the contract specs use to resolve a registration's component; update the widget-embedding spec §2 to §4 and §7 | Host spec covers: eager widget unchanged, lazy widget loading then ready, load failure then fallback and retry on the next placement, type changed mid-load, second placement without a pause, unknown type |
| 2 Documents | The node view awaits, with teardown and rejection handling; document specs | A lazy widget embedded in a document renders; removing the node before the load finishes leaves no component behind |
| 3 Calendar | `calendar.widget.ts` uses `loadComponent`; its contract spec awaits | Testbed production build is back near **9.9 MB initial** with the calendar still registered; the Base Calendar samples page and a calendar in a designed page both render; decide whether to lower the budget back to 10 MB |
| 4 Preload (optional) | `preloadWidgets` called when the shell activates an `AppDefinition` route | No visible gap before a calendar appears on first load of a page that places it |

## 5. Releases and compatibility

`@processpuzzle/widgets` is published and consumed by independent libraries, so the order matters: widgets (minor; `component`
becomes optional, which is the one change a consumer reading `.component` has to absorb), then base-document, then the
feature libraries that switch to `loadComponent`, each raising its `peerDependencies` range on widgets. A feature library on
the new range cannot run on an old widgets library, and that is the intended failure mode.

## 6. Risks and open questions

- **Layout shift** when a widget arrives after its neighbours; reserve space in the host if it shows in practice.
- **Specs that render widgets synchronously** need `whenStable`; grep the consumers of `WidgetHostComponent` first.
- **Angular 22's `resource` API**: check its status before using it in a shared library; a signal plus an effect is the fallback.
- **Error budget**: raised twice (8 to 10 MB in October, 10 to 11 MB now) with no stated reason in the first. When phase 3
  is done, lower it again or write down why not.
- **Route-level providers** are a cheaper partial option that needs no registry change (a lazy route providing
  `provideCalendarWidgets()` makes the widget available in its subtree only), but it does not cover pages the shell renders
  at the root. It stays available if phase 1 is postponed.

## 7. Not in scope

Lazy *definitions* (they are small and the designer UI needs them synchronously), module federation, and registering widgets
from the server.

## 8. As built (2026-11-20)

- **Phase 1:** `WidgetRegistration` is `component` *or* `loadComponent`. `loadWidgetComponent` / `loadedWidgetComponent`
  (`widget-registry/widget-component-loader.ts`) memoize per registration and do not cache a rejection. `WidgetHostComponent`
  shows a `loading` placeholder, or a `load-failed` one that retries on the next placement; a late load is cached but not
  rendered. The clash rule compares the class or the loader. Host tests cover the loading state, failure then retry, and a
  second placement rendering at once.
- **Phase 2:** the document node view mounts a lazy widget once loaded; a generation counter, bumped by every teardown,
  drops a load that finishes after the node was re-rendered or removed.
- **Phase 3:** `calendar.widget.ts` uses `loadComponent`. The main entry point of base-calendar re-exports the component
  **type only**: a value re-export kept it in the initial chunk, which the build warned about. The testbed samples page
  places the calendar through `pp-widget-host` instead of importing the component. The production build splits
  `calendar-component` (327 kB raw, 80 kB transferred) out of the initial chunk; **initial total 9.96 MB**.
- **Budget:** kept at the 11 MB error budget. Lowering it to 10 MB would leave 40 kB of headroom, so the next small
  change would fail the build.
- **Not done:** phase 4 (preload). Package versions were not bumped: the release order in §5 applies when the packages are
  published.

