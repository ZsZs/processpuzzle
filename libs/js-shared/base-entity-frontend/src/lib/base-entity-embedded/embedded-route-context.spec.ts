import { ActivatedRouteSnapshot, UrlSegment } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { snakeCaseName } from '../base-form-navigator/base-form-navigator.store';
import { readEmbeddedBreadcrumb, readEmbeddedRouteChain, resolveEmbeddedRouteContext } from './embedded-route-context';

function segments(...paths: string[]): UrlSegment[] {
  return paths.map((path) => ({ path }) as UrlSegment);
}

/**
 * A stand-in for the snapshot chain the router builds. The entity name sits on the container route and the
 * id on its `:entityId/details` child — and the router **inherits** `data` down to that child, which the
 * stub reproduces because reconstructing the levels despite that repetition is the whole job. Each route
 * also carries the URL segments it matched, the way the router's own snapshots do.
 */
function routeChain(...levels: Array<{ entityName?: string; entityId?: string }>): ActivatedRouteSnapshot {
  let deepest: ActivatedRouteSnapshot | null = null;

  let params: Record<string, string> = {};
  for (const level of levels) {
    const data = level.entityName ? { entityName: level.entityName } : {};
    const url = level.entityName ? segments(snakeCaseName(level.entityName)) : [];
    // The container inherits its ancestors' params, so it sees the *owner's* entityId without declaring one.
    const container = { data, params, url, parent: deepest, routeConfig: { path: 'container' } } as unknown as ActivatedRouteSnapshot;
    deepest = container;
    if (level.entityId !== undefined) {
      params = { ...params, entityId: level.entityId };
      deepest = { data, params, url: segments(level.entityId, 'details'), parent: container, routeConfig: { path: ':entityId/details' } } as unknown as ActivatedRouteSnapshot;
    }
  }

  return deepest as ActivatedRouteSnapshot;
}

function embeddedAttr(attrName: string, linkedEntityType: string, referenceIdField?: string): BaseEntityAttrDescriptor {
  const attr = new BaseEntityAttrDescriptor(attrName, FormControlType.EMBEDDED_COMPONENTS);
  attr.linkedEntityType = linkedEntityType;
  if (referenceIdField) attr.referenceIdField = referenceIdField;
  return attr;
}

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

const descriptorOf = (entityName: string) => descriptors[entityName];

const aggregate = {
  id: '1',
  embeddedComponents: [
    { id: 'embedded_1_1', embeddedDetails: [{ id: 'detail_a' }, { id: 'detail_b' }] },
    { id: 'embedded_1_2' },
  ],
};

describe('readEmbeddedRouteChain', () => {
  it('pairs each entity with the id of its details route', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'embedded_1_1' }));

    expect(chain).toEqual([
      { entityName: 'Test Entity', entityId: '1' },
      { entityName: 'Embedded Component', entityId: 'embedded_1_1' },
    ]);
  });

  it('reports a level with no id, which is what a list route looks like', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component' }));

    expect(chain[1]).toEqual({ entityName: 'Embedded Component', entityId: undefined });
  });

  /** The router repeats the container's `data` on its details child; that echo is one level, not two. */
  it('treats a details route echoing its container as the same level', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }));

    expect(chain).toEqual([{ entityName: 'Test Entity', entityId: '1' }]);
  });

  /** `App Nav Item` nests inside itself, so a repeat *after* an id is a genuinely new level. */
  it('opens a new level when a child type nests inside itself', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'App Definition', entityId: 'demo' }, { entityName: 'App Nav Item', entityId: 'a' }, { entityName: 'App Nav Item', entityId: 'b' }));

    expect(chain).toEqual([
      { entityName: 'App Definition', entityId: 'demo' },
      { entityName: 'App Nav Item', entityId: 'a' },
      { entityName: 'App Nav Item', entityId: 'b' },
    ]);
  });

  it('ignores routes that name no entity', () => {
    const chain = readEmbeddedRouteChain(routeChain({}, {}, { entityName: 'Test Entity', entityId: '1' }));

    expect(chain).toEqual([{ entityName: 'Test Entity', entityId: '1' }]);
  });

  it('returns nothing for an empty chain', () => {
    expect(readEmbeddedRouteChain(null)).toEqual([]);
  });
});

describe('readEmbeddedBreadcrumb', () => {
  it('gives every level the URL of its own screen', () => {
    const crumbs = readEmbeddedBreadcrumb(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'embedded_1_1' }));

    expect(crumbs.map((crumb) => crumb.url)).toEqual(['/test-entity/1/details', '/test-entity/1/details/embedded-component/embedded_1_1/details']);
  });

  /** What a sibling list or details URL is built on — and, for an embedded level, the owner's own form. */
  it('stops each level’s base URL before its own entity segment', () => {
    const crumbs = readEmbeddedBreadcrumb(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'embedded_1_1' }));

    expect(crumbs.map((crumb) => crumb.baseUrl)).toEqual(['', '/test-entity/1/details']);
  });

  it('ends a level with no row named at its own branch', () => {
    const crumbs = readEmbeddedBreadcrumb(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component' }));

    expect(crumbs[1]).toEqual({ entityName: 'Embedded Component', entityId: undefined, url: '/test-entity/1/details/embedded-component', baseUrl: '/test-entity/1/details' });
  });

  /** `App Nav Item` nests inside itself, so the repeated segment has to produce two distinct URLs. */
  it('walks a self-nesting child one level at a time', () => {
    const crumbs = readEmbeddedBreadcrumb(routeChain({ entityName: 'App Definition', entityId: 'demo' }, { entityName: 'App Nav Item', entityId: 'a' }, { entityName: 'App Nav Item', entityId: 'b' }));

    expect(crumbs.map((crumb) => crumb.url)).toEqual([
      '/app-definition/demo/details',
      '/app-definition/demo/details/app-nav-item/a/details',
      '/app-definition/demo/details/app-nav-item/a/details/app-nav-item/b/details',
    ]);
  });

  it('returns nothing for an empty chain', () => {
    expect(readEmbeddedBreadcrumb(null)).toEqual([]);
  });
});

describe('resolveEmbeddedRouteContext', () => {
  it('addresses the root’s own embedded attribute with an empty path', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'embedded_1_1' }));

    expect(resolveEmbeddedRouteContext(chain, aggregate, descriptorOf)).toEqual({
      rootEntityName: 'Test Entity',
      rootId: '1',
      path: [],
      attrName: 'embeddedComponents',
      entityName: 'Embedded Component',
      referenceIdField: 'id',
    });
  });

  /** The hop is resolved against the data, because the URL carries the row's key and not its position. */
  it('resolves an intermediate level to its position in the aggregate', () => {
    const chain = readEmbeddedRouteChain(
      routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'embedded_1_2' }, { entityName: 'Embedded Detail', entityId: 'detail_a' }),
    );

    expect(resolveEmbeddedRouteContext(chain, aggregate, descriptorOf)).toMatchObject({
      path: [{ attrName: 'embeddedComponents', index: 1 }],
      attrName: 'embeddedDetails',
      entityName: 'Embedded Detail',
    });
  });

  it('resolves a level whose own row does not exist yet, which is what creating one looks like', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'new' }));

    expect(resolveEmbeddedRouteContext(chain, aggregate, descriptorOf)).toMatchObject({ path: [], attrName: 'embeddedComponents' });
  });

  it('gives up on a stale link whose intermediate row is gone', () => {
    const chain = readEmbeddedRouteChain(
      routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Embedded Component', entityId: 'deleted' }, { entityName: 'Embedded Detail', entityId: 'detail_a' }),
    );

    expect(resolveEmbeddedRouteContext(chain, aggregate, descriptorOf)).toBeUndefined();
  });

  it('gives up when the owner does not declare the child as an embedded attribute', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }, { entityName: 'Unrelated', entityId: 'x' }));

    expect(resolveEmbeddedRouteContext(chain, aggregate, descriptorOf)).toBeUndefined();
  });

  it('gives up when the chain names no embedded level, or no root instance', () => {
    const rootOnly = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity', entityId: '1' }));
    const listRoute = readEmbeddedRouteChain(routeChain({ entityName: 'Test Entity' }, { entityName: 'Embedded Component', entityId: 'embedded_1_1' }));

    expect(resolveEmbeddedRouteContext(rootOnly, aggregate, descriptorOf)).toBeUndefined();
    expect(resolveEmbeddedRouteContext(listRoute, aggregate, descriptorOf)).toBeUndefined();
  });
});

describe('BaseEntityDescriptor.embeddedAttrFor', () => {
  it('finds the attribute carrying a child type', () => {
    expect(descriptors['Test Entity'].embeddedAttrFor('Embedded Component')?.attrName).toBe('embeddedComponents');
    expect(descriptors['Test Entity'].embeddedAttrFor('Nothing')).toBeUndefined();
  });

  /** The URL segment names the entity, so two attributes offering the same child would be ambiguous. */
  it('refuses two attributes carrying the same child type, naming both', () => {
    const ambiguous = new BaseEntityDescriptor({
      entityName: 'Ambiguous',
      attrDescriptors: [embeddedAttr('primary', 'Embedded Component'), embeddedAttr('secondary', 'Embedded Component')],
    });

    expect(() => ambiguous.embeddedAttrFor('Embedded Component')).toThrowError(/'primary', 'secondary'/);
  });
});
