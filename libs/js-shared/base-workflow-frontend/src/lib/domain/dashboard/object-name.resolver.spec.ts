import { TestBed } from '@angular/core/testing';
import { BASE_ENTITY_FACADE_REGISTRY, DynamicEntityRegistry, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObjectNameResolver } from './object-name.resolver';

/** A facade is all the resolver reads: a service to fetch the record by, and the attribute that titles it. */
class OrderFacade {
  readonly findById = vi.fn((id: string) => (id === 'missing' ? throwError(() => new Error('404')) : of({ id, orderNumber: `ORD-${id}` })));
  readonly service = { findById: this.findById };
  readonly descriptor = { titleAttrName: () => 'orderNumber' };
}

describe('ObjectNameResolver', () => {
  let resolver: ObjectNameResolver;
  let facade: OrderFacade;
  const definitions = { load: vi.fn(async () => new Map([['special-order', { code: 'special-order', name: 'Special Order' }]])) };
  const dynamicFacade = new OrderFacade();
  const dynamicEntities = { resolve: vi.fn(async (name: string) => (name === 'Special Order' ? { facade: dynamicFacade } : undefined)) };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        OrderFacade,
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { Order: OrderFacade } },
        { provide: EntityDefinitionRegistry, useValue: definitions },
        { provide: DynamicEntityRegistry, useValue: dynamicEntities },
      ],
    });
    resolver = TestBed.inject(ObjectNameResolver);
    facade = TestBed.inject(OrderFacade);
  });

  const settle = () => new Promise((resolve) => setTimeout(resolve));

  // The seed's `artifactTypeId` is `order`; the descriptor's entity is `Order`.
  it('names a record by the attribute its descriptor titles records with', async () => {
    resolver.request('order', '42');
    await settle();

    expect(resolver.nameOf('order', '42')).toBe('ORD-42');
  });

  it('knows nothing until the record has been asked for', () => {
    expect(resolver.nameOf('order', '42')).toBeUndefined();
  });

  it('asks for a record once', async () => {
    resolver.request('order', '42');
    resolver.request('order', '42');
    await settle();

    expect(facade.findById).toHaveBeenCalledTimes(1);
  });

  it('leaves a record it cannot read unnamed', async () => {
    resolver.request('order', 'missing');
    await settle();

    expect(resolver.nameOf('order', 'missing')).toBeUndefined();
  });

  it('leaves a record of an entity nobody registered unnamed', async () => {
    resolver.request('invoice', '7');
    await settle();

    expect(resolver.nameOf('invoice', '7')).toBeUndefined();
  });

  // An entity defined as metadata rather than compiled in: found through its definition, by code or name.
  it('names a record of a metadata entity', async () => {
    resolver.request('special_order', '9');
    await settle();

    expect(dynamicEntities.resolve).toHaveBeenCalledWith('Special Order');
    expect(resolver.nameOf('special_order', '9')).toBe('ORD-9');
  });
});
