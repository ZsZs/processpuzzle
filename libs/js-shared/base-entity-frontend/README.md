# @processpuzzle/base-entity

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-entity.yml/badge.svg)
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

Each visible field gets a `BaseEntityAttrDescriptor`. `FormControlType` covers `TEXT_BOX`, `TEXTAREA`, `CHECKBOX`, `DATE`, `DROPDOWN`, `LOOKUP`, `RADIO`, `TAGS`, `ARTIFACT`, `COMPONENTS`, `FOREIGN_KEY`, `LABEL`, `TITLE`, and `FLEX_BOX`. Use `FlexboxDescriptor` to lay attributes out in rows and columns, and `linkedEntityType` (a string — the related entity's name) to point at a related entity (used by `LOOKUP`, `FOREIGN_KEY`, and nested `COMPONENTS`). The descriptor is resolved at runtime through `BASE_ENTITY_FACADE_REGISTRY`.

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
  const componentsAttr = new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS, 'Components');

  lookupAttr.linkedEntityType = 'Trunk Data';
  componentsAttr.linkedEntityType = 'Test Entity Component';

  const column1 = new FlexboxDescriptor([nameAttr, descriptionAttr, booleanAttr], FlexDirection.COLUMN);
  const column2 = new FlexboxDescriptor([numberAttr, dateAttr, lookupAttr, enumAttr, componentsAttr], FlexDirection.COLUMN);
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
