import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { DynamicEntityRegistry } from './dynamic-entity.registry';
import { EntityDefinition } from './entity-definition';
import { ORDER_DEFINITION, ORDER_LINE_DEFINITION, TEST_ENTITY_DEFINITIONS } from './test-entity-definition';

const ENTITY_DEFINITIONS_URL = 'http://localhost:8080/organizations/acme/entity-definitions?page=0&size=200';

describe('DynamicEntityRegistry', () => {
  let registry: DynamicEntityRegistry;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' } } },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: {} },
      ],
    });
    registry = TestBed.inject(DynamicEntityRegistry);
    controller = TestBed.inject(HttpTestingController);
  });

  /** Definitions have to be flushed while `resolve` is awaiting them. */
  async function resolve(entityName: string, definitions: EntityDefinition[] = TEST_ENTITY_DEFINITIONS) {
    const resolving = registry.resolve(entityName);
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: definitions });
    return resolving;
  }

  it('builds a descriptor with a store bound into it, which is what the tabs, list and form all read', async () => {
    const resolved = await resolve('Order');

    expect(resolved?.descriptor.entityName).toBe('Order');
    expect(resolved?.descriptor.store).toBeDefined();
    expect(resolved?.descriptor.store).toBe(resolved?.facade.store);
  });

  it('gives the store the definition defaults, so a new-row form opens with the authored values', async () => {
    const resolved = await resolve('Order');
    const store = resolved?.facade.store as { createEntity(): Record<string, unknown> };

    expect(store.createEntity()['status']).toBe('DRAFT');
  });

  /**
   * The aggregate, not just the entity asked for: an embedded child needs a descriptor and a store of its
   * own before its rows can be listed on the parent's form, and doing it in one pass is what makes the
   * parent's form work on its first render.
   */
  it('resolves the embedded children the aggregate carries, in the same pass', async () => {
    await resolve('Order');

    expect(registry.descriptorOf('Order Line')?.entityName).toBe('Order Line');
    expect(registry.storeOf('Order Line')).toBeDefined();
    expect(registry.facadeTokenOf('Order Line')).toBeDefined();
  });

  it('answers undefined for an entity this tenant has no definition for', async () => {
    expect(await resolve('Nowhere')).toBeUndefined();
    expect(await registry.resolve(undefined)).toBeUndefined();
  });

  it('answers nothing about an entity before it is resolved, so no route can be built on a token that would throw', () => {
    expect(registry.descriptorOf('Order')).toBeUndefined();
    expect(registry.storeOf('Order')).toBeUndefined();
    expect(registry.facadeTokenOf('Order')).toBeUndefined();
    expect(registry.descriptorOf(undefined)).toBeUndefined();
  });

  it('caches per entity, so the same descriptor and store serve every route of it', async () => {
    const first = await resolve('Order');
    const second = await registry.resolve('Order');

    expect(second).toBe(first);
    expect(registry.facadeTokenOf('Order')).toBe(first?.facadeToken);
    // Not `verify()`: creating the store loads its first page, which every entity store does on init.
    controller.expectNone(ENTITY_DEFINITIONS_URL);
  });

  /** A second token for one entity would give its routes a second facade, and therefore a second store. */
  it('hands out one facade token per entity, resolving to the facade it built', async () => {
    const resolved = await resolve('Order');
    expect(resolved).toBeDefined();

    expect(TestBed.inject(resolved?.facadeToken as never)).toBe(resolved?.facade);
  });

  it('builds an embedded child on an embedded facade — its repository writes the containing document', async () => {
    await resolve('Order');

    expect(registry.descriptorOf('Order Line')?.isEmbedded).toBe(true);
    expect(registry.descriptorOf('Order')?.isEmbedded).toBe(false);
  });

  // App Nav Item nests in itself. The definition graph is finite; the tree it describes is not.
  it('terminates on a child type that nests inside itself', async () => {
    const selfNesting: EntityDefinition = {
      code: 'nav-item',
      name: 'Nav Item',
      isEmbedded: true,
      componentParents: ['nav-item'],
      attributes: [
        { code: 'label', formControlType: 'TEXT', isLinkToDetails: true },
        { code: 'children', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'nav-item' },
      ],
    };

    const resolved = await resolve('Nav Item', [selfNesting]);

    expect(resolved?.descriptor.entityName).toBe('Nav Item');
    expect(resolved?.descriptor.embeddedAttrFor('Nav Item')?.attrName).toBe('children');
  });

  it('terminates on a cycle between two definitions', async () => {
    const left: EntityDefinition = { code: 'left', name: 'Left', attributes: [{ code: 'rights', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'right' }] };
    const right: EntityDefinition = { code: 'right', name: 'Right', attributes: [{ code: 'lefts', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'left' }] };

    await resolve('Left', [left, right]);

    expect(registry.descriptorOf('Left')).toBeDefined();
    expect(registry.descriptorOf('Right')).toBeDefined();
  });

  it('rebuilds after a reset, so an edited definition takes effect', async () => {
    const first = await resolve('Order');

    registry.reset();
    expect(registry.descriptorOf('Order')).toBeUndefined();

    const second = await resolve('Order');
    expect(second).not.toBe(first);
    // The token was handed to the router before the reset, and an InjectionToken factory runs once — so it
    // has to keep answering, now with the rebuilt facade.
    expect(TestBed.inject(second?.facadeToken as never)).toBe(second?.facade);
  });

  /**
   * The seam that makes this cost the rest of the library nothing: `EmbeddedAggregateAccessor` and
   * `resolveEmbeddedRouteContext` look descriptors and stores up here, synchronously, by name.
   */
  describe('through BaseEntityDescriptorRegistry', () => {
    it('answers for a metadata entity once it is resolved', async () => {
      await resolve('Order');
      const descriptorRegistry = TestBed.inject(BaseEntityDescriptorRegistry);

      expect(descriptorRegistry.getDescriptor('Order')?.entityName).toBe('Order');
      expect(descriptorRegistry.getStore('Order')).toBe(registry.storeOf('Order'));
      expect(descriptorRegistry.getDescriptor('Order Line')?.entityName).toBe('Order Line');
    });

    it('still answers nothing for an entity that is neither compiled in nor defined', async () => {
      await resolve('Order');

      expect(TestBed.inject(BaseEntityDescriptorRegistry).getDescriptor('Nowhere')).toBeUndefined();
    });
  });

  describe('descriptor synthesis of the seeded order aggregate', () => {
    it('carries the containment both ways, which is what the embedded route context walks', async () => {
      await resolve('Order', TEST_ENTITY_DEFINITIONS);

      const order = registry.descriptorOf('Order');
      const orderLine = registry.descriptorOf('Order Line');
      expect(order?.embeddedAttrFor(ORDER_LINE_DEFINITION.name)?.attrName).toBe('lineItems');
      expect(orderLine?.isComponentOf(ORDER_DEFINITION.name)).toBe(true);
    });
  });
});
