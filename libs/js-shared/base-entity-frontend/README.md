# @processpuzzle/base-entity

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-entity-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_entity&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_entity)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fbase-entity?style=flat)](https://www.npmjs.com/package/@processpuzzle/base-entity)

## Introduction

`@processpuzzle/base-entity` is a run-time form and table generator for Angular, driven by two descriptor objects: a **Base Entity Descriptor** that describes the entity (and its related entities), and **Base Entity Attribute Descriptors** that specify how each attribute is presented.

<a href="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-bird-eye-view.png?raw=true">
  <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-bird-eye-view.png?raw=true" width=600px alt="Bird eye view">
</a>

From these **Inputs:**

- **Entity Descriptor** – describes the subject entity and (optionally) its linked entities.
- **Entity Attribute Descriptors** – specify the presentation style of each attribute.

the library generates these **Outputs:**

- A **Reactive Angular Form** built dynamically at run-time.
- An **Angular Material Table** built dynamically at run-time.
- A **descriptor-aware RSQL search** with an assisted advanced query editor (see [RSQL search](#rsql-search)).
- A **client-side PDF export** of the table, driven by the same descriptors (see [PDF export](#pdf-export)).

The diagram below shows the main classes the library exposes:

<a href="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-architectural-context.png?raw=true">
  <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-architectural-context.png?raw=true" width=600px alt="Architectural Context">
</a>

- **BaseEntity** – interface your custom entity implements (only requires an `id`).
- **BaseEntityRestService / BaseEntityFirestoreService** – concrete CRUD services to extend; both implement the `BaseEntityService` contract.
- **BaseEntityMapper** – interface for translating between your DTO and your entity. `SimpleEntityMapper` covers the trivial case.
- **BaseEntityStore** – `@ngrx/signals` store feature backing the generated components. Composes with `BaseEntityTabsStore`, `BaseEntityContainerStore`, and the singleton `BaseFormNavigatorStore`.
- **BaseEntityFacade** – optional one-stop class that wires entity, mapper, service, store, and descriptor together.
- **BaseEntityContainerComponent** – host component that renders the table and the form together.
- **BaseEntityListComponent** – Angular Material table of your entities.
- **BaseEntityFormComponent** – reactive form for CRUD on a single entity.

## Usage

To plug the library into your application you provide an entity class, a mapper, a service, a store, and a descriptor. Each step is a small amount of configuration — most of the behavior comes from the base classes.

<a href="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-design_overview.png?raw=true">
  <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-design_overview.png?raw=true" width=600px alt="Design Overview">
</a>

The snippets below are taken from the [TestEntity sample in the testbed](https://github.com/ZsZs/processpuzzle/tree/develop/apps/processpuzzle-testbed/src/app/content/base-forms/test-entity).

### 1. Define your entity

`BaseEntity` only requires `id: string`. Anything else is yours to model freely.

```typescript
export class TestEntity implements BaseEntity {
  readonly id: string;
  private name: string;
  private description: string | undefined;
  private boolean: boolean;
  private number: number;
  private date: Date;
  private lookup: string;
  private enumValue: TestEnum;
  private artifact?: ArtifactAttr;
  private tags?: Array<string>;
  private components?: Array<TestEntityComponent>;

  constructor(id?: string, name?: string /* … */) {
    this.id = id ?? uuidv4();
    // …
  }
}
```

### 2. Provide a mapper

A mapper translates between the DTO returned by the backend and your entity class. `SimpleEntityMapper` works when no translation is needed; otherwise implement `BaseEntityMapper<Entity>` and use the exported `getEnumKeyByValue` / `getEnumValueByKey` helpers for enum round-tripping. Child entities are mapped by delegating to their own mappers.

```typescript
@Injectable({ providedIn: 'root' })
export class TestEntityMapper implements BaseEntityMapper<TestEntity> {
  private readonly componentMapper = inject(TestEntityComponentMapper);

  fromDto(dto: any): TestEntity {
    return new TestEntity(
      dto.id, dto.name, dto.description, dto.boolean, dto.number, dto.date, dto.lookup,
      getEnumKeyByValue<TestEnum>(TestEnum, dto.enumValue),
      dto.artifact,
      dto.tags,
      dto.components?.map((c: any) => this.componentMapper.fromDto(c)),
    );
  }

  toDto(entity: TestEntity): any {
    const dto = { ...entity } as any;
    return { ...dto, enumValue: getEnumValueByKey<TestEnum>(TestEnum, dto.enumValue) };
  }
}
```

### 3. Extend the data service

Pick `BaseEntityRestService` for an HTTP backend or `BaseEntityFirestoreService` for Firestore. Pass the mapper, a configuration key that resolves to the backend root URL, and the resource path.

```typescript
@Injectable({ providedIn: 'root' })
export class TestEntityService extends BaseEntityRestService<TestEntity> {
  constructor(protected override entityMapper: TestEntityMapper) {
    super(entityMapper, 'BACKEND_SERVICE_ROOT', 'test-entity');
  }
}
```

You can add custom data-access methods on top of the inherited CRUD operations.

### 4. Compose a signal store

The generated components read and write through an `@ngrx/signals` store. `BaseEntityStore` provides CRUD state and the Material table data source; the other features add tab state, container filtering, and form-navigation behavior.

```typescript
export const TestEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntity>(TestEntity, () => inject(TestEntityService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
```

`BaseFormNavigatorStore` is a singleton that you do not add per entity — inject it where you need navigation between list and details views.

### 5. Describe the attributes

Each visible field gets a `BaseEntityAttrDescriptor` whose `FormControlType` picks the control (full list and behavior in [Control types](#control-types)). Use `FlexboxDescriptor` to lay attributes out in rows and columns, and `linkedEntityType` (a string — the related entity's name) to point at another entity (used by `LOOKUP`, `FOREIGN_KEY`, `RELATED_ENTITIES`, `COMPONENTS`, and `EMBEDDED_COMPONENTS`). The descriptor is resolved at runtime through `BASE_ENTITY_FACADE_REGISTRY`.

```typescript
function createTestEntityAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  const booleanAttr = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Boolean');
  const numberAttr = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number', undefined, false, { inputType: 'number' });
  const dateAttr = new BaseEntityAttrDescriptor('date', FormControlType.DATE, 'Date', undefined, false, { inputType: 'date' });
  const lookupAttr = new BaseEntityAttrDescriptor('lookup', FormControlType.LOOKUP, 'Lookup');
  const enumAttr = new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN, 'Enum', selectables);
  const relatedEntitiesAttr = new BaseEntityAttrDescriptor('relatedEntities', FormControlType.RELATED_ENTITIES, 'Related Entities');
  const componentsAttr = new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS, 'Components');
  const embeddedComponentsAttr = new BaseEntityAttrDescriptor('embeddedComponents', FormControlType.EMBEDDED_COMPONENTS, 'Embedded Components');

  lookupAttr.linkedEntityType = 'Trunk Data';
  relatedEntitiesAttr.linkedEntityType = 'Related Entity';
  componentsAttr.linkedEntityType = 'Test Entity Component';
  embeddedComponentsAttr.linkedEntityType = 'Embedded Component';

  const column1 = new FlexboxDescriptor([nameAttr, descriptionAttr, booleanAttr], FlexDirection.COLUMN);
  const column2 = new FlexboxDescriptor([numberAttr, dateAttr, lookupAttr, enumAttr, relatedEntitiesAttr, componentsAttr, embeddedComponentsAttr], FlexDirection.COLUMN);
  const layout = new FlexboxDescriptor([column1, column2], FlexDirection.CONTAINER);
  layout.style = { 'column-gap': '20px' };
  return [layout];
}

export function createTestEntityDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: 'Test Entity', attrDescriptors: createTestEntityAttrDescriptors() });
}
```

A few notes:

- `linkedEntityType` is just the name of the related entity. The actual `BaseEntityDescriptor` is resolved at runtime through the facade registered in `BASE_ENTITY_FACADE_REGISTRY`.
- `BaseEntityDescriptor` takes an options object (`{ entityName, attrDescriptors, store?, entityTitle? }`); `store` and `entityTitle` are usually set later in the host component.

### 6. Render the container

Pass the descriptor to `BaseEntityContainerComponent` and attach the store. The container then drives both the list and the form views.

```typescript
@Component({
  selector: 'test-entity-container',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: `<base-entity-container [entityDescriptor]="baseEntityDescriptor"></base-entity-container>`,
})
export class TestEntityContainerComponent {
  private store = inject(TestEntityStore);
  baseEntityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.baseEntityDescriptor = createTestEntityDescriptor();
    this.baseEntityDescriptor.store = this.store;
    this.baseEntityDescriptor.entityTitle = () => this.store.currentEntity()?.name ?? '';
  }
}
```

### Optional: bundle everything in a Facade

When you have many entities, `BaseEntityFacade` centralises the wiring (mapper, service, store, descriptor) so a single token can drive routed views. Extend it and declare `entityType`, `entityName`, and `attrDescriptors`; override the `create…` hooks to return your concrete classes.

```typescript
@Injectable()
export class TestEntityFacade extends BaseEntityFacade<TestEntity> {
  readonly entityType = TestEntity;
  readonly entityName = 'Test Entity';
  readonly attrDescriptors = createTestEntityDescriptor().attrDescriptors;

  private readonly mapperRef = inject(TestEntityMapper);
  private readonly serviceRef = inject(TestEntityService);

  protected override createMapper() { return this.mapperRef; }
  protected override createService() { return this.serviceRef; }
  protected override createStoreClass(): Type<unknown> { return TestEntityStore; }
}
```

The facade can then be provided through the `ACTIVE_ENTITY_FACADE` token, and `BaseEntityContainerComponent` will resolve its descriptor automatically — no per-entity container component required.

## Control types

Every attribute's `FormControlType` selects the component that renders it in the generated form. The builder creates a reactive `FormControl` under `attrName` for each descriptor and instantiates the mapped component; **an unmapped type throws `Undefined form control type`**.

| Type | Renders | Value shape | Required props | Notes |
| --- | --- | --- | --- | --- |
| `TEXT_BOX` | `matInput` text field | `string` | `label` | `options.inputType` sets the HTML input type (e.g. `number`). |
| `TEXTAREA` | `matInput` textarea | `string` | `label` | `lines` sets the row count; `placeholder` the hint. |
| `CHECKBOX` | `mat-checkbox` | `boolean` | `label` | |
| `DATE` | `matInput` + `mat-datepicker` | `Date` | `label` | Format is governed by the ambient Material date adapter. |
| `DROPDOWN` | `mat-select` | selected option's `value` | **`selectables`** | Options come from `selectables` (array or `() => Selectable[]`). |
| `RADIO` | native radio group | selectable `key` | **`selectables`** | Reactive; stores the `key` of the chosen option. |
| `TITLE` | `<h2>` section heading (display only) | — | `label` | Renders `label` as a form section title. |
| `TAGS` | `mat-chip-grid` | `string[]` | `label` | ENTER/COMMA add a chip; chips are editable/removable. |
| `LABEL` | `<h3>`/`<p>` (display only) | `string` | `label` | `isHeading` renders a heading instead of a paragraph. |
| `FLEX_BOX` | layout container | — (no control) | uses `FlexboxDescriptor` | Grouping only; recurses into children. See below. |
| `LOOKUP` | autocomplete over a lookup table | lookup **`key`** (`string`) | **`linkedEntityType`** | Tricky — see below. |
| `FOREIGN_KEY` | read-only field + Select/link buttons | related entity **`id`** (`string`) | **`linkedEntityType`** | To-one reference; picks via the navigator. |
| `RELATED_ENTITIES` | list of related-entity rows | array of related **ids** | **`linkedEntityType`**, `referenceIdField` | To-many **association**; the related entities exist independently. |
| `COMPONENTS` | list of component rows | array of component **ids** | **`linkedEntityType`**, `referenceIdField` | To-many **containment**, child in its own table. Attaching stamps the parent's id into the child's foreign key; deleting destroys the child. |
| `EMBEDDED_COMPONENTS` | inline sub-form per component | array of the **children themselves** | **`linkedEntityType`** | To-many **containment**, child inside the parent's payload. Backed by a `FormArray`; saved and deleted with the parent. |
| `ARTIFACT` | file thumbnail/icon + upload/delete | `ArtifactAttr` (or `null`) | `label`, `showThumbnail` | Tricky — see below. |
| `ADDITIONAL_PROPERTIES` | editable key/value list | `Record<string,string>` | `label` | Free-form string map. |

`linkedEntityType` is **mandatory** for `LOOKUP`, `FOREIGN_KEY`, `RELATED_ENTITIES`, `COMPONENTS`, and `EMBEDDED_COMPONENTS` — it names the other entity, whose descriptor is resolved at runtime through `BASE_ENTITY_FACADE_REGISTRY` (or, for an embedded component, through `EMBEDDED_ENTITY_DESCRIPTOR_REGISTRY`); omitting it throws.

### Association vs. containment

Three control types render a to-many list, and which one is right follows from **who owns the rows**:

| Control type | Relationship | Add | Delete | Persisted by |
| --- | --- | --- | --- | --- |
| `RELATED_ENTITIES` | **association** — the target lives on its own | picks an existing entity through the navigator | detaches the reference only | the target's own save, independently |
| `COMPONENTS` | **containment**, child in its own table | picks/creates a child, then stamps this entity's id into the child's foreign key | destroys the child (after confirmation) | the child's own endpoint |
| `EMBEDDED_COMPONENTS` | **containment**, child in this payload | appends an empty inline sub-form | drops the row | this entity's save |

The two containment types are the two variations of a **component** — a part that belongs to exactly one
parent — and the child's own descriptor says which it is through `componentParent` / `isEmbedded` (see
[Component entities](#component-entities)). The declarations are on opposite sides of the relationship and are
both hand-written, so each control **checks that they agree at first render** and throws, naming both sides,
when they do not:

- the child entity has no descriptor in either registry;
- the child does not name this entity in its `componentParent`;
- `COMPONENTS` points at an `isEmbedded` child (use `EMBEDDED_COMPONENTS`), or `EMBEDDED_COMPONENTS` points at
  one that is not (use `COMPONENTS`).

`Test Entity` in the [testbed](../../../apps/processpuzzle-testbed) demonstrates all three side by side —
`relatedEntities` → `Related Entity`, `components` → `Test Entity Component`, `embeddedComponents` →
`Embedded Component`.

### FLEX_BOX (layout)

`FLEX_BOX` uses a `FlexboxDescriptor` (not a `BaseEntityAttrDescriptor`) and carries **no value**. It nests child descriptors and a `FlexDirection` (`CONTAINER` / `COLUMN` / `ROW`); the builder recurses, rendering the children into the *same* form group. Use it to arrange fields in rows and columns (see the `createTestEntityAttrDescriptors` example above).

### The tricky ones

- **`LOOKUP`** uses a **two-control design**: the visible autocomplete text is a private `displayControl` decoupled from the real `FormControl`, which stores the lookup **key**. An `effect()` keeps them in sync. The lookup table (`{ key, value, description? }`) is loaded on init from the store resolved via `linkedEntityType`; the display shows `value`, the form holds `key`. The link icon navigates to the related entity.
- **`FOREIGN_KEY`** shows the related entity's identifying text (read-only) with the real id in a hidden control. The **Select** button snapshots the form and navigates to the related list (`SELECT_OR_CREATE`); on return the chosen id is written back to both the entity and the control. A link icon navigates to the current reference.
- **`RELATED_ENTITIES`** is the to-many analogue: it normalizes heterogeneous items (strings/objects) into `{ id }` using `referenceIdField`, appends via the same navigator round-trip, and each row has a link and a delete button that detaches the reference only.
- **`COMPONENTS`** looks like `RELATED_ENTITIES` but owns its rows. It resolves each row's text through the child's store (loading it if the parent's form is the first screen opened) and falls back to the id; **Add** takes the same navigator round-trip and then writes the parent's id into the attribute named by the child's `parentReferenceAttrName()`; **Delete** confirms and then calls the child store's `delete`. Like `ARTIFACT`, it therefore has an **immediate backend side-effect**, before the parent is saved.
- **`EMBEDDED_COMPONENTS`** is the only control backed by a **`FormArray`** rather than a `FormControl`: each row is a sub-form built from the child's descriptor with the same `BaseEntityFormBuilder`, so the children are already part of `form.value` (no imperative write-back) and a child's `required` attribute keeps the *parent's* Save disabled. The sub-forms are handed the **parent's** store — an embedded child has none of its own. Rows are seeded from the attribute's value, which is also what restores them from a form snapshot.
- **`ARTIFACT`** binds an `ArtifactAttr` (`{ bucket, objectId, name, mimeType }`). It fetches a thumbnail only for `image/*` types when `showThumbnail !== false`, otherwise shows a MIME-type icon; upload delegates to `ArtifactSelectorComponent` and **delete actually removes the stored object** (after a confirmation dialog). With `isHeading` it degrades to a plain heading. Requires an `ObjectStoreService` provider.

These value-editing controls (`ARTIFACT`, `LOOKUP`, `FOREIGN_KEY`, `RELATED_ENTITIES`, `COMPONENTS`, `TAGS`, `ADDITIONAL_PROPERTIES`) write to their `FormControl` **imperatively** (`setValue` + `markAsDirty`), which is why the form's Save button reacts to `form.events` rather than a plain dirty binding. `EMBEDDED_COMPONENTS` is the exception: its `FormArray` is edited by the sub-forms themselves.

> **Note.** `DATE` display/parse format follows Angular Material's ambient date adapter (`MAT_DATE_FORMATS`), configured app-wide rather than per field, so the descriptor's `format` property is not applied here.

## Component entities

Most entities are aggregate roots — they stand on their own and are reached from their own list. A
**component** is a part: it belongs to exactly one parent, is created from the parent's form, and is deleted
outright rather than merely detached when the user removes it there. Two `BaseEntityDescriptor` options
declare that:

| Option | Type | Meaning |
| --- | --- | --- |
| `componentParent` | `string \| string[]` | Entity name(s) that may aggregate this one. A list is allowed because one component *type* can be hosted by several parent types (an `App Widget` sits under `App Region`, `App Page` and `App Widget`) — the 1:N invariant holds per *instance*, not per type. Undefined for a stand-alone entity. |
| `isEmbedded` | `boolean` | `true` when the payload travels inside the parent's payload — no endpoint and no store of its own, and the parent's save persists it. `false` (default) when the component is persisted on its own and points back at its parent through a `FOREIGN_KEY` attribute. Declaring it without `componentParent` throws. |

```typescript
// Embedded: an App Region has no endpoint; it is a slot inside the App Definition document.
new BaseEntityDescriptor({ entityName: 'App Region', attrDescriptors, componentParent: 'App Definition', isEmbedded: true });

// Not embedded: persisted through its own store, with `testEntityId` as the key back to the parent.
new BaseEntityDescriptor({ entityName: 'Test Entity Component', attrDescriptors, componentParent: 'Test Entity', isEmbedded: false });
```

The parent points back at the component with a `COMPONENTS` (not embedded) or `EMBEDDED_COMPONENTS` (embedded)
attribute — see [Association vs. containment](#association-vs-containment) for what the two control types do and
for the errors thrown when the two declarations disagree.

Derived from those two:

- **`isComponent()`** / **`isComponentOf(entityName)`** — containment predicates.
- **`parentReferenceAttrName()`** — the `FOREIGN_KEY` attribute pointing at one of the `componentParent`s,
  found through nested `FlexboxDescriptor`s. `undefined` for a stand-alone entity, and for an embedded
  component, which is located by its position in the parent's payload rather than by a key.

> **`componentParent` is not `parentEntity`.** `parentEntity` names the **inheritance** supertype and pairs
> with `isAbstract`; `componentParent` names the **aggregator**. An entity can legitimately have both.

Both properties are serialized by `EntityRegistryComponent`, so a Low-Code designer reading the registry sees
the containment graph alongside the inheritance one.

### Registering an embedded component

A non-embedded component is a normal entity: it has a service, a store and therefore a facade, and its
descriptor is resolved through `BASE_ENTITY_FACADE_REGISTRY` like any other. An **embedded** one has nothing to
put in a facade — no endpoint, no store — yet the parent's form still has to resolve its descriptor to render
the sub-forms. It is registered instead in `EMBEDDED_ENTITY_DESCRIPTOR_REGISTRY`:

```typescript
{
  provide: EMBEDDED_ENTITY_DESCRIPTOR_REGISTRY,
  useValue: { 'Embedded Component': createEmbeddedComponentDescriptor },
}
```

- The values are **factories** (`() => BaseEntityDescriptor`), not instances, because a containment graph may be
  cyclic (`App Definition` → `App Region` → `App Widget` → `App Widget`), which eager values cannot express.
  `BaseEntityDescriptorRegistry` memoizes what a factory returns, so the descriptor's identity is stable across
  renders.
- `BaseEntityDescriptorRegistry.getDescriptor()` consults the facade registry **first** and falls back to this
  one; `getStore()` keeps returning `undefined` for an embedded entity.
- Such an entity needs no route, container component, service or store — only the entity class, its mapper and
  its descriptor. Its i18n scope is loaded on the **parent's** route, since its labels are rendered there.
- `EntityRegistryComponent` serializes it too, with no `route`, and the generated e2e list/CRUD suites skip
  `isEmbedded` entities because there is no page to visit.

## Per-field styling

Two descriptor properties give a consumer per-attribute style control, applied via `[ngClass]`:

- **`styleClass`** — class(es) on the field **wrapper** (`fieldset` / `mat-form-field` / the radio container).
- **`labelClass`** — class(es) on the field **label** (`legend` / `mat-label` / checkbox label).

There is also a `style` property bound with `[ngStyle]` for one-off inline styles.

```typescript
const expr = new BaseEntityAttrDescriptor('expression', FormControlType.TEXTAREA, 'Expression');
expr.styleClass = 'monospace full-width';
expr.labelClass = 'muted';
```

> **These classes must be defined in a global stylesheet** (your app's `styles.scss` / the `styles` array), **not** in a component's encapsulated `.css`. The classes land on the *library's* DOM elements; Angular's emulated view encapsulation scopes a component's own styles to that component's elements, so a component-scoped `.monospace` rule would be applied to the field but never match it. Global styles (or `ViewEncapsulation.None`) are the reliable seam — prefer this over reaching into the library's internal tags, which are not a stable contract.

## Internationalisation

Entity names (shown in the tabs) and attribute labels (column headers and form labels) can be translated by the **consuming application** through [Transloco](https://jsverse.github.io/transloco/). The library follows **convention over configuration**: the key root is derived from the `entityName`, and you only provide the matching translation files — no per-field wiring. When a key is missing, the raw `entityName` / `label` from the descriptor is used as the fallback, so translation is entirely opt-in.

### 1. Key root is derived from the entity name

The transloco key root is `snake_underscore(entityName)` — `"Trunk Data"` → `trunk_data`, `"OrderLine"` → `order_line`. No configuration is needed; just name the Transloco scope to match. Set `i18nScope` on the descriptor only to **override** the derived root (e.g. when the registered scope name differs from the convention, or when the derived root would collide with the library's own `base_entity` scope).

```typescript
new BaseEntityDescriptor({
  entityName: 'Trunk Data',
  attrDescriptors: createTrunkDataAttrDescriptors(),
  // key root → 'trunk_data' (derived); pass i18nScope only to override
});
```

### 2. Provide the Transloco scope on the route

The scope must be provided where the components render (typically the routed feature). **Always set `alias` explicitly** — Transloco camelCases the default alias, which silently breaks names containing `-` or `_`.

```typescript
{
  path: 'trunk_data',
  providers: [provideTranslocoScope({ scope: 'trunk_data', alias: 'trunk_data' })],
  // …
}
```

### 3. Add the translation files

Transloco loads scoped files from `assets/i18n/<scope>/<lang>.json`. Because the scope (= derived key root) already identifies the entity, keys are **rooted at the scope** (Transloco prepends the scope automatically, so the file content is flat — no entity wrapper):

- **Entity name** → `<scope>._self` (the reserved `_self` avoids colliding with the attribute keys)
- **Attribute label** → `<scope>.<attrName>`

where `<attrName>` is the attribute's `attrName` (a code identifier — no spaces, no display strings). An attribute's `labelKey` overrides its own segment (replaces `attrName`); use plain identifiers — spaces are not valid in Transloco keys.

```jsonc
// assets/i18n/trunk_data/en.json  → keys trunk_data._self, trunk_data.key, …
{
  "_self": "Trunk Data",   // entity name in the tabs
  "key": "Reference Key",  // attribute labels, keyed by attrName
  "description": "Description",
  "value": "Value"
}
```

### The library's own strings

Everything the library renders that is *not* derived from a descriptor — toolbar labels and tooltips, PDF-export
and RSQL dialogs, the delete-confirmation dialogs, and the list/details tab captions — lives in the `base_entity` scope shipped with the package
(`assets/i18n/base_entity/<lang>.json`, copied into your build). Provide it once on the route that hosts the
entity screens:

```typescript
providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })];
```

The tab captions take the resolved entity name as a parameter, so translators control the word order:

```jsonc
"tabs": {
  "list": "{{ entity }} - list",      // → "Trunk Data - list"
  "details": "{{ entity }} - details"
}
```

## Status-bar title

The status bar labels the selected entity with the value of one identifying attribute. That attribute
is `titleKey` on the descriptor, defaulting to the `isLinkToDetails` attribute (the same one
`componentIdentification()` returns). Set `titleKey` only to point at a different attribute; set the
`entityTitle` string/function to override the displayed text entirely.

```typescript
new BaseEntityDescriptor({ entityName: 'Order Line', attrDescriptors, titleKey: 'productName' });
// status bar shows currentEntity().productName
```

## RSQL search

The list toolbar carries two independent search inputs:

- **Filter** — a client-side, case-insensitive substring match over the rows already loaded into the Material table. Instant, but limited to the current page of data.
- **Query** — a server-side [RSQL/FIQL](https://github.com/jirutka/rsql-parser) expression. On <kbd>Enter</kbd> (or the ▶ button) the toolbar calls `store.load({ query })`; the REST service forwards it to the backend as a `where=<rsql>` request parameter (`BaseEntityFirestoreService` applies the equivalent constraints). Clearing the query reloads the unfiltered list.

### Query syntax

RSQL combines comparisons with logical operators:

| Kind | Tokens |
|------|--------|
| Comparison | `==`  `!=`  `=gt=`  `=ge=`  `=lt=`  `=le=`  `=in=`  `=out=`  `=like=` |
| Logic | `;` (AND)  `,` (OR)  `( … )` (grouping) |
| Values | unquoted, `'single'`/`"double"` quoted, or a list `field=in=(a,b,c)` |

```text
status==active;createdAt=gt='2026-01-01'
name=like='*foo*',priority=ge=3
```

### Advanced editor

The **✎ (`edit_note`)** button in the query field opens the **advanced query editor** dialog — a CodeMirror-based editor (`RsqlEditorDialog` → `RsqlQueryEditorComponent`) that provides:

- **Syntax highlighting** and bracket matching for RSQL.
- **Autocomplete** of field names, the operators valid for each field's type, and enum values.
- **Live linting** — structural validation (paren balance, clause ordering, unterminated strings) plus semantic checks (unknown field, operator not allowed for the field's type). **Apply** stays disabled while the expression is invalid.
- A **sample query** inserted on <kbd>Tab</kbd> in an empty editor, plus a matching placeholder.

Both the autocomplete and the linter are driven by field metadata derived from the entity's descriptor by `DescriptorBackedFieldMetadataProvider`: each `BaseEntityAttrDescriptor` maps to an RSQL field whose type (and therefore its allowed operators) comes from the `FormControlType` — `CHECKBOX`→boolean, `DATE`→date, `DROPDOWN`/`RADIO`→enum (enum values taken from the attribute's selectables), a numeric/date `TEXT_BOX` input type→number/date, everything else→string. Presentation-only controls (`ARTIFACT`, `FLEX_BOX`, `LABEL`, `RELATED_ENTITIES`, `TITLE`) are excluded from the searchable fields.

To use the editor outside the entity toolbar, provide your own `RsqlFieldMetadataProvider` and bind `RsqlQueryEditorComponent` through a reactive `FormControl` (see `query-editor/example-usage.ts`).

## PDF export

The list toolbar can export the current entities to a PDF **entirely on the client** — no backend round-trip. When the list view is active and the store holds at least one entity, `BaseEntityToolbarComponent` shows a **PDF** action (the familiar `picture_as_pdf` icon). It appears both as a toolbar button and, on small screens, as a menu item.

Clicking it opens a small options dialog (orientation, page size, page-footer toggle) — deliberately not a full layout editor — and then generates and downloads the file.

What ends up in the PDF is derived from the very same descriptors that drive the table:

- **Columns** come from the entity's `BaseEntityAttrDescriptor`s, flattened through nested `FlexboxDescriptor`s. Any attribute marked `hideInTable = true` is dropped — so **a field hidden from the list is also absent from the PDF**.
- **Cell rendering** is chosen from each attribute's `FormControlType`: `CHECKBOX` becomes `✓`/`✗` (centered), `DATE` is formatted as a locale date, `TAGS` are joined, and `ARTIFACT` shows the artifact name.
- The document **title** uses the descriptor's `entityTitle` (falling back to `entityName`), with a record-count subtitle and page footers.

The heavy `jspdf` / `jspdf-autotable` dependencies are **lazy-loaded on first export**, so they never enter the initial bundle.

### Programmatic use

The export is also usable outside the toolbar. The public API exposes `PdfExportService`, the `entityDescriptorToPdfColumns` mapper, the `PdfExportOptionsDialog`, and the `PdfColumnDefinition` / `PdfExportOptions` / `PdfExportResult` types.

```typescript
private readonly pdfExport = inject(PdfExportService);

async export(descriptor: BaseEntityDescriptor, entities: BaseEntity[]) {
  const columns = entityDescriptorToPdfColumns(descriptor.attrDescriptors);
  const result = await this.pdfExport.export(entities as Record<string, unknown>[], columns, {
    title: 'Test Entities',
    filename: 'test-entity-export',
    orientation: 'landscape',
  });
  // result: { success, filename, rowCount, error? }
}
```

Column headers, cell text, and dialog labels are translated through the `base_entity` Transloco scope (keys under `pdf_export`).
