# Entity screens — mounting a descriptor at a route

Everything needed to put one entity's generated List and Details screens — with the tab bar, toolbar and
status bar — at a route, whichever way the entity was declared.

This is the seam a **host application** uses. It deliberately knows nothing about `AppDefinition`s:
base-app is one caller, not a prerequisite. An application whose entities are all metadata-defined needs
this layer and `base-entity-definition/`, and no base-app at all.

## The pieces

- `entity-screens.resolver.ts` — `EntityScreenResolver.resolve(entityName)` → `{ descriptor, embeddedChildren }`.
  Answers for an entity with a compile-time `BaseEntityFacade` in `BASE_ENTITY_FACADE_REGISTRY` (which
  **wins**, being the host's explicit decision) and for one that exists only as a `BaseEntityDefinition`,
  synthesized by `base-entity-definition/`. `undefined` when neither answers.
- `entity-screen-routes.ts` — `entityScreenRoute({ entityName, screens, hostPath, data })` → `Route`.
  Returns the route rather than registering it, so it composes into a hand-written route config or into
  whatever a host generates. `path` is the caller's to set.
- `entity-screens.component.ts` — `BaseEntityScreensComponent`, the component that route mounts. Reads the
  descriptor off route `data` (the route builder already resolved it) and hands it to
  `BaseEntityContainerComponent`; renders "no entity type registered for '…' yet" when nothing answered.

## The URL shape, and why it is not negotiable

`BaseFormNavigatorSingletonStore` composes every entity URL as `<base>/<snakeCaseName(entityName)>/list`
or `<base>/<snakeCaseName(entityName)>/<id>/details`, and derives `<base>` by counting segments back from
the current URL. So the entity's own segment has to be **in** the URL:

```
<host path>/order/list
<host path>/order/<uuid>/details
<host path>/order/<uuid>/details/order-line/<productName>/details
```

`entityScreenRoute` emits that `<snake>` child plus an empty-path redirect into its list. Pass `hostPath`
and a route already called `order` gets a path-less group instead, so the URL stays `…/order/list` rather
than `…/order/order/list`.

A host that got this wrong would not see an error — every tab click and row link would simply do nothing,
because the URL the navigator built matched no route.

## Mounting them somewhere

```ts
const screens = await inject(EntityScreenResolver).resolve('Order');
const routes: Routes = [{ path: 'orders', ...entityScreenRoute({ entityName: 'Order', screens, hostPath: 'orders' }) }];
```

Resolution is asynchronous — a metadata entity's definitions have to be fetched — so a static route config
needs the same trick base-app uses: an empty `children` array filled by a `canMatch` guard, or a
`loadChildren`. See `appShellRoutesGuard` in base-app for a worked example.
