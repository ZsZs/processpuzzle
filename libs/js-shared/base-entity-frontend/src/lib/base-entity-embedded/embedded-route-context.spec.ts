import { ActivatedRouteSnapshot, UrlSegment } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { snakeCaseName } from '../base-form-navigator/base-form-navigator.store';
import { aggregateChainOf, readEmbeddedBreadcrumb, readEmbeddedRouteChain, resolveEmbeddedRouteContext } from './embedded-route-context';

function segments(...paths: string[]): UrlSegment[] {
  return paths.map((path) => ({ path }) as UrlSegment);
}

/**
 * A stand-in for the snapshot chain the router builds. The entity name sits on the container route and the
 * id on its `:entityId/details` child — and the router **inherits** both `data` and `params` down to that
 * child and to everything below it, which the stub reproduces because reconstructing the levels despite that
 * inheritance is the whole job. So `data` is the inherited merge on every snapshot, while `routeConfig.data`
 * carries only what the route itself declares. Each route also carries the URL segments it matched.
 */
function routeChain(...levels: Array<{ entityName?: string; entityId?: string }>): ActivatedRouteSnapshot {
  let deepest: ActivatedRouteSnapshot | null = null;

  let params: Record<string, string> = {};
  let data: Record<string, string> = {};
  for (const level of levels) {
    if (level.entityName) data = { ...data, entityName: level.entityName };
    const url = level.entityName ? segments(snakeCaseName(level.entityName)) : [];
    // The container inherits its ancestors' params, so it sees the *owner's* entityId without declaring one.
    const declared = level.entityName ? { entityName: level.entityName } : undefined;
    const container = { data, params, url, parent: deepest, routeConfig: { path: 'container', data: declared } } as unknown as ActivatedRouteSnapshot;
    deepest = container;
    if (level.entityId !== undefined) {
      params = { ...params, entityId: level.entityId };
      // Declares no name of its own — it only inherits its container's.
      deepest = { data, params, url: segments(level.entityId, 'details'), parent: container, routeConfig: { path: ':entityId/details' } } as unknown as ActivatedRouteSnapshot;
    }
  }

  return deepest as ActivatedRouteSnapshot;
}

/** Routes appended below `parent` that declare nothing and only inherit its data — a hosted application's own. */
function inheritingRoutes(parent: ActivatedRouteSnapshot, ...paths: string[]): ActivatedRouteSnapshot {
  let deepest = parent;
  for (const path of paths) {
    deepest = { data: parent.data, params: parent.params, url: segments(path), parent: deepest, routeConfig: { path } } as unknown as ActivatedRouteSnapshot;
  }
  return deepest;
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

describe('readEmbeddedBreadcrumb, for routes that only inherited an entity name', () => {
  /**
   * The designer's Preview tab: `app-definition` declares the name, `:entityId/preview` supplies the id, and
   * the previewed application's own routes below it inherit the name — reported *after* an id had been seen,
   * which the repeat rule reads as a genuinely new level. The status bar showed `Demo › Demo`.
   */
  it('counts a name once, from the route that declares it', () => {
    const preview = routeChain({ entityName: 'Test Entity', entityId: '1' });

    const breadcrumb = readEmbeddedBreadcrumb(inheritingRoutes(preview, 'home'));

    expect(breadcrumb.map((level) => level.entityName)).toEqual(['Test Entity']);
  });

  it('counts it once however deep the hosted routes go', () => {
    const preview = routeChain({ entityName: 'Test Entity', entityId: '1' });

    const breadcrumb = readEmbeddedBreadcrumb(inheritingRoutes(preview, 'back-office', 'orders'));

    expect(breadcrumb.map((level) => level.entityName)).toEqual(['Test Entity']);
  });

  /** The hosted routes still contribute their segments, so the level's own URLs stay navigable. */
  it('leaves the level URL at the screen the level owns', () => {
    const preview = routeChain({ entityName: 'Test Entity', entityId: '1' });

    const breadcrumb = readEmbeddedBreadcrumb(inheritingRoutes(preview, 'home'));

    expect(breadcrumb[0].url).toBe('/test-entity/1/details/home');
    expect(breadcrumb[0].baseUrl).toBe('');
  });

  it('still counts a genuinely self-nesting child, which declares the name again', () => {
    const chain = readEmbeddedRouteChain(routeChain({ entityName: 'Embedded Component', entityId: 'a' }, { entityName: 'Embedded Component', entityId: 'b' }));

    expect(chain).toEqual([
      { entityName: 'Embedded Component', entityId: 'a' },
      { entityName: 'Embedded Component', entityId: 'b' },
    ]);
  });
});

describe('aggregateChainOf', () => {
  const chain = (...levels: Array<[string, string | undefined]>) => levels.map(([entityName, entityId]) => ({ entityName, entityId }));

  it('keeps a chain that is one aggregate from end to end', () => {
    const levels = chain(['Test Entity', '1'], ['Embedded Component', 'embedded_1_1'], ['Embedded Detail', 'detail_a']);

    expect(aggregateChainOf(levels, descriptorOf)).toEqual(levels);
  });

  /**
   * The designer's Preview tab: a previewed application's screens are mounted below `app-definition/<id>`,
   * so the chain starts at an entity that carries nothing embedded at all. Reading it as the aggregate root
   * left every embedded list in a previewed application empty.
   */
  it('drops the levels above the aggregate — an entity the URL merely passes through', () => {
    const levels = chain(['App Definition', 'demo'], ['Test Entity', '1'], ['Embedded Component', 'embedded_1_1']);

    expect(aggregateChainOf(levels, descriptorOf)).toEqual(levels.slice(1));
  });

  it('drops several such levels, however deep the hosting screens nest', () => {
    const levels = chain(['App Definition', 'demo'], ['App Region', 'sidenav'], ['Test Entity', '1'], ['Embedded Component', 'embedded_1_1']);

    expect(aggregateChainOf(levels, descriptorOf)).toEqual(levels.slice(2));
  });

  it('leaves a single level alone — there is no aggregate to find and nothing to drop', () => {
    const levels = chain(['Test Entity', '1']);

    expect(aggregateChainOf(levels, descriptorOf)).toEqual(levels);
  });

  it('answers with the deepest level alone when its owner does not carry it, so the caller resolves nothing', () => {
    const levels = chain(['Test Entity', '1'], ['Embedded Detail', 'detail_a']);

    expect(aggregateChainOf(levels, descriptorOf)).toEqual(levels.slice(1));
  });

  it('stops at a level whose descriptor is unknown rather than assuming containment', () => {
    const levels = chain(['Nowhere', 'x'], ['Test Entity', '1'], ['Embedded Component', 'embedded_1_1']);

    expect(aggregateChainOf(levels, descriptorOf)).toEqual(levels.slice(1));
  });

  it('keeps a child type that nests inside itself', () => {
    const selfNesting = {
      'Nav Item': new BaseEntityDescriptor({ entityName: 'Nav Item', attrDescriptors: [embeddedAttr('children', 'Nav Item')] }),
    };
    const levels = chain(['Nav Item', 'a'], ['Nav Item', 'b'], ['Nav Item', 'c']);

    expect(aggregateChainOf(levels, (entityName) => selfNesting[entityName as 'Nav Item'])).toEqual(levels);
  });

  it('preserves the extra fields of a breadcrumb level, so it can slice a breadcrumb as well as a chain', () => {
    const levels = [
      { entityName: 'App Definition', entityId: 'demo', url: '/app-definition/demo', baseUrl: '' },
      { entityName: 'Test Entity', entityId: '1', url: '/test-entity/1/details', baseUrl: '/test-entity' },
    ];

    expect(aggregateChainOf(levels, descriptorOf)).toEqual([levels[1]]);
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
