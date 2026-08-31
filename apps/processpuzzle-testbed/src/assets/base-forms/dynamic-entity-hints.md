### A fully dynamic entity

Nothing about `Dynamic Entity` is compiled into this application — no entity class, no descriptor, no mapper,
no service, no store, no facade, no translations. Its definition lives in the backend; the screens below are
synthesized from that definition at run-time. The whole of the application's side of it is the route quoted
in step 3.

#### 1. Where the definition lives

`libs/java-shared/base-entity-backend/src/main/resources/default-entities/processpuzzle-testbed-entities.yaml`,
under `code: dynamic-entity`, `name: Dynamic Entity`, imported on startup by `DefaultEntityLoader`. The
frontend reads it over the contract, from `ENTITY_SERVICE_ROOT` (which falls back to `APP_SERVICE_ROOT`):

```
GET /organizations/{orgKey}/entity-definitions       the types
GET /organizations/{orgKey}/entities/dynamic-entity  the rows
```

#### 2. Two layers, not one

- **Knowledge layer** — `BaseEntityDefinition` + `BaseEntityAttribute`: what the type *is*. Twelve
  attributes here — text, textarea, number, boolean, date, date-time, enum, multi-valued tags, artifact, a
  self-referencing lookup, and two embedded levels.
- **Operation layer** — `EntityObject`, a JSONB payload keyed by attribute code: the rows themselves,
  Alpha…Epsilon.

Neither gets a generated class: `EntityDefinition` is a contract interface and `DynamicEntity` is a bag of
values keyed by attribute code. The definition is what gives that bag meaning.

#### 3. The recipe — mount it in three lines

```typescript
export async function dynamicEntityScreenRoutes(): Promise<Routes> {
  const screens = await inject(EntityScreenResolver).resolve('Dynamic Entity');
  return [{ path: '', ...entityScreenRoute({ entityName: 'Dynamic Entity', screens, hostPath: 'dynamic-entity' }) }];
}
```

used as this route's `loadChildren`:

```typescript
{
  path: 'dynamic-entity',
  loadComponent: () => import('./dynamic-entity-container.component').then((c) => c.DynamicEntityContainerComponent),
  loadChildren: dynamicEntityScreenRoutes,
}
```

Note what is **absent**: no `ACTIVE_ENTITY_FACADE`, no `provideTranslocoScope`, no `BASE_ENTITY_ROUTES`. The
synthesized descriptor carries its own store, `baseEntityRoutes` binds a facade per embedded branch itself,
and every label comes from the definition. `loadChildren` — rather than resolving in the component — is what
makes the `await` possible: the router calls it inside an injection context and waits before activating a
child route.

#### 4. The URLs it produces

```
dynamic-entity/list
dynamic-entity/<uuid>/details
dynamic-entity/<uuid>/details/dynamic-embedded-detail/<key>/details
```

`<key>` is not an id — an embedded row has none. It lives *inside* its owner's payload, so it is addressed
by position, and the URL carries the value of the child's identifying attribute for `indexOfRow` to find
that position by: `dynamic-embedded-detail/CPU/details`. Which attribute that is comes from the child
definition — the one with `isLinkToDetails: true`, or, failing that, its leading attribute. Author a child
whose identifying attribute is not unique among one owner's rows and the second row of a pair opens the
first.

The entity's own snake-case segment has to be **in** the URL: `BaseFormNavigatorSingletonStore` composes
every entity URL as `<base>/snakeCaseName(entityName)/list`, counting segments back from the current one.
Mount the screens at a path that disagrees and every tab click silently does nothing. Passing `hostPath` is
how `entityScreenRoute` knows the segment is already there and mounts its group path-less, rather than
producing `dynamic-entity/dynamic-entity/list`.

#### 5. How to refer to one elsewhere

By descriptor **name** — `'Dynamic Entity'`, the same string an `AppDefinition` route's `entityName` carries,
and the same string `EntityScreenResolver.resolve()` takes. `snakeCaseName(name)` turns it into the URL
segment. A definition's `code` is the backend's key and appears in resource URLs; the `name` is the identity
everywhere on the frontend.

#### 6. A compile-time facade wins

Adding `'Dynamic Entity': SomeFacade` to `BASE_ENTITY_FACADE_REGISTRY` would **suppress** all of the above:
`EntityScreenResolver` prefers a registered facade by design, because registering one is the host
application's explicit decision to contribute something a definition cannot express — an extra tab, a
hand-tuned layout. That is why neither `Dynamic Entity` nor the demo application's `Order` has an entry.

#### 7. What it needs to run

The Java backend, which imports the seed file because `apps/processpuzzle-backend`'s `application.yaml` sets
`base-entity.loadDefaultEntities: yes`, and a run-time configuration pointing at it: every stage's
`APP_SERVICE_ROOT` does — `http://localhost:8080/organizations/processpuzzle-testbed` in `config.dev.json`
and `config.ci.json`. Nothing here is served by the json-server mock on `:3000`; that stands in for
third-party sources only (see `tools/mock-backend/README.md`).

Point `APP_SERVICE_ROOT` at a host that serves no `entity-definitions` and `EntityDefinitionService`
answers an empty list, so this page reads *"No entity type registered for 'Dynamic Entity' yet."* That is
the designed degradation, not a defect: whatever names an entity — an `AppDefinition` route, a
hand-written route like this one — is allowed to be ahead of what is deployed, and the link that leads
here still has to render something.
