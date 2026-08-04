import { InjectionToken, Provider, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { EmbeddedAggregateAccessor, ResolvedEmbeddedAggregate } from './embedded-aggregate.accessor';

const aggregate = {
  id: '1',
  embeddedComponents: [{ id: 'embedded_1_1', embeddedDetails: [{ id: 'detail_a' }] }, { id: 'embedded_1_2' }],
};

function embeddedAttr(attrName: string, linkedEntityType: string): BaseEntityAttrDescriptor {
  const attr = new BaseEntityAttrDescriptor(attrName, FormControlType.EMBEDDED_COMPONENTS);
  attr.linkedEntityType = linkedEntityType;
  return attr;
}

/** Builds the snapshot chain the router would have produced, `data` and `params` inheritance included. */
function routerStateFor(levels: Array<{ entityName: string; entityId?: string }>): Router {
  const root = { data: {}, params: {}, parent: null, firstChild: null } as unknown as ActivatedRouteSnapshot;
  let deepest = root;

  let params: Record<string, string> = {};
  for (const level of levels) {
    const data = { entityName: level.entityName };
    const container = { data, params, parent: deepest, firstChild: null, routeConfig: { path: 'container' } } as unknown as ActivatedRouteSnapshot;
    Reflect.set(deepest, 'firstChild', container);
    deepest = container;
    if (level.entityId !== undefined) {
      params = { ...params, entityId: level.entityId };
      const details = { data, params, parent: container, firstChild: null, routeConfig: { path: ':entityId/details' } } as unknown as ActivatedRouteSnapshot;
      Reflect.set(container, 'firstChild', details);
      deepest = details;
    }
  }

  return { routerState: { snapshot: { root } } } as unknown as Router;
}

function setupAccessor(levels: Array<{ entityName: string; entityId?: string }>, { rootLoaded = true } = {}) {
  const rootStore = {
    loadById: vi.fn((id: string) => (rootLoaded && id === '1' ? aggregate : undefined)),
    currentEntity: signal(rootLoaded ? aggregate : undefined),
    update: vi.fn(() => Promise.resolve(undefined)),
  };
  const rootFacade = new InjectionToken<unknown>('ROOT_FACADE');

  const descriptors: Record<string, BaseEntityDescriptor> = {
    'Test Entity': new BaseEntityDescriptor({ entityName: 'Test Entity', attrDescriptors: [embeddedAttr('embeddedComponents', 'Embedded Component')] }),
    'Embedded Component': new BaseEntityDescriptor({
      entityName: 'Embedded Component',
      attrDescriptors: [embeddedAttr('embeddedDetails', 'Embedded Detail')],
      componentParent: 'Test Entity',
      isEmbedded: true,
    }),
    'Embedded Detail': new BaseEntityDescriptor({ entityName: 'Embedded Detail', attrDescriptors: [], componentParent: 'Embedded Component', isEmbedded: true }),
  };

  const facadeTokens: Record<string, InjectionToken<unknown>> = { 'Test Entity': rootFacade };
  const providers: Provider[] = [
    { provide: Router, useValue: routerStateFor(levels) },
    { provide: rootFacade, useValue: { descriptor: descriptors['Test Entity'], store: rootStore } },
  ];
  for (const entityName of ['Embedded Component', 'Embedded Detail']) {
    const token = new InjectionToken<unknown>(entityName);
    facadeTokens[entityName] = token;
    providers.push({ provide: token, useValue: { descriptor: descriptors[entityName], store: {} } });
  }
  providers.push({ provide: BASE_ENTITY_FACADE_REGISTRY, useValue: facadeTokens });

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers });
  return { accessor: TestBed.inject(EmbeddedAggregateAccessor), rootStore };
}

describe('EmbeddedAggregateAccessor', () => {
  it('resolves the child of the entity whose form is open', () => {
    const { accessor } = setupAccessor([
      { entityName: 'Test Entity', entityId: '1' },
      { entityName: 'Embedded Component', entityId: 'embedded_1_1' },
    ]);

    expect(accessor.resolve('Embedded Component')?.context).toMatchObject({ rootEntityName: 'Test Entity', rootId: '1', path: [], attrName: 'embeddedComponents' });
  });

  /**
   * A nested attribute is listed on its owner's form, so the deepest active route is still the owner's — the
   * level being asked about has to be appended. This is what lets one form host the list of the next level.
   */
  it('appends the level being asked about when no route for it is active yet', () => {
    const { accessor } = setupAccessor([
      { entityName: 'Test Entity', entityId: '1' },
      { entityName: 'Embedded Component', entityId: 'embedded_1_1' },
    ]);

    expect(accessor.resolve('Embedded Detail')?.context).toMatchObject({ path: [{ attrName: 'embeddedComponents', index: 0 }], attrName: 'embeddedDetails' });
  });

  it('truncates the chain at the level being asked about when a deeper route is active', () => {
    const { accessor } = setupAccessor([
      { entityName: 'Test Entity', entityId: '1' },
      { entityName: 'Embedded Component', entityId: 'embedded_1_2' },
      { entityName: 'Embedded Detail', entityId: 'detail_a' },
    ]);

    expect(accessor.resolve('Embedded Component')?.context).toMatchObject({ path: [], attrName: 'embeddedComponents' });
  });

  it('resolves nothing when no aggregate is open', () => {
    const { accessor } = setupAccessor([{ entityName: 'Test Entity', entityId: '1' }]);

    expect(accessor.resolve('Embedded Component')).toBeDefined();
    expect(accessor.resolve('Test Entity')).toBeUndefined();
  });

  it('resolves nothing while the containing document has not loaded', () => {
    const { accessor } = setupAccessor([{ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component' }], { rootLoaded: false });

    expect(accessor.resolve('Embedded Component')).toBeUndefined();
  });

  it('persists an embedded write as a write of the containing document', async () => {
    const { accessor, rootStore } = setupAccessor([
      { entityName: 'Test Entity', entityId: '1' },
      { entityName: 'Embedded Component', entityId: 'embedded_1_1' },
    ]);
    const resolved = accessor.resolve('Embedded Component');
    expect(resolved).toBeDefined();

    await accessor.writeRoot(resolved as ResolvedEmbeddedAggregate, { id: '1', embeddedComponents: [] });

    expect(rootStore.update).toHaveBeenCalledWith({ id: '1', embeddedComponents: [] });
  });
});
