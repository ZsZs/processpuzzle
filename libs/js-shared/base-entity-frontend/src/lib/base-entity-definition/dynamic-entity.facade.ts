import { Type } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { EmbeddedEntityFacade } from '../base-entity-facade/embedded-entity.facade';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { DynamicEntity, EntityDefinition } from './entity-definition';
import { DynamicEntityService } from './dynamic-entity.service';

/**
 * The class a metadata-defined entity's store instantiates for a new row.
 *
 * A class rather than an object literal because `BaseEntityFacade` needs a `Type<Entity>`: the store calls
 * `new entityType()` in `createEntity()`, and `entityNameFromType()` reads `.name` off it for the devtools
 * label. One class per definition, named after it, so two entities do not share a devtools store name.
 *
 * The constructor seeds the attributes' authored `defaultValue`s, which is what makes a new-row form open
 * with `status: DRAFT` rather than empty — `BaseEntityFormBuilder` reads each control's initial value off
 * the entity the store created.
 */
export function dynamicEntityTypeOf(definition: EntityDefinition): Type<DynamicEntity> {
  const defaults = Object.fromEntries((definition.attributes ?? []).filter((attribute) => attribute.defaultValue !== undefined).map((attribute) => [attribute.code, attribute.defaultValue]));

  const entityType = class {
    id?: string;
    version?: number;
    [attrName: string]: unknown;
    constructor() {
      Object.assign(this, defaults);
    }
  };

  // The class is anonymous, so its `name` is '' until it is set — and `entityNameFromType()` would then
  // fall back to 'base-entity' for every dynamic store, collapsing them into one devtools entry.
  Object.defineProperty(entityType, 'name', { value: definition.name });
  return entityType;
}

/**
 * Facade of an entity that exists only as metadata.
 *
 * Everything a hand-written facade contributes is contributed here from a `BaseEntityDefinition` instead:
 * the descriptor (synthesized by `descriptorOf`), the repository (`DynamicEntityService` over the
 * definition's own collection) and — inherited untouched — the signal store with its tabs and container
 * state. That is what lets `BaseEntityContainerComponent`, the list, the form, the toolbar and the status
 * bar work on a designer-created entity with no change of their own.
 *
 * Instantiated by `DynamicEntityRegistry` rather than provided as a token, because there is no token to
 * declare at compile time: which entities exist is a fact about the tenant's data. It must be constructed
 * inside an injection context (`runInInjectionContext`) — the base class injects `Injector` in a field
 * initializer.
 *
 * Deliberately **not** `@Injectable()`, unlike every hand-written facade: its constructor parameters are a
 * definition and a descriptor, which are not injection tokens, and the decorator makes the compiler demand
 * that they be (NG2003). Nothing is lost — the decorator is what lets Angular *construct* a class, and this
 * one is constructed by hand.
 */
export class DynamicEntityFacade extends BaseEntityFacade<DynamicEntity> {
  readonly entityType: Type<DynamicEntity>;

  constructor(
    private readonly definition: EntityDefinition,
    private readonly synthesizedDescriptor: BaseEntityDescriptor,
  ) {
    super();
    this.entityType = dynamicEntityTypeOf(definition);
  }

  /** The `entities/{code}` collection. Bypasses the base class's `backendRoot`/`endpoint` pair, which
   * expects the two to be declared as fields by a subclass written by hand. */
  protected override createService(): BaseEntityService<DynamicEntity> {
    return new DynamicEntityService(this.definition.code);
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return this.synthesizedDescriptor;
  }
}

/**
 * Facade of a metadata-defined entity whose payload travels inside its parent's — an
 * `isEmbedded: true` definition.
 *
 * Differs from {@link DynamicEntityFacade} in exactly what `EmbeddedEntityFacade` already differs in: the
 * repository reads and writes the containing entity's document rather than a collection of its own, and
 * the store is keyed by the row's `referenceIdField` rather than by `id`. Both come from the base class;
 * this only supplies the descriptor and the row type.
 *
 * Undecorated for the same reason as {@link DynamicEntityFacade}.
 */
export class DynamicEmbeddedEntityFacade extends EmbeddedEntityFacade<DynamicEntity> {
  readonly entityType: Type<DynamicEntity>;

  constructor(definition: EntityDefinition, private readonly synthesizedDescriptor: BaseEntityDescriptor) {
    super();
    this.entityType = dynamicEntityTypeOf(definition);
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return this.synthesizedDescriptor;
  }
}
