import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InjectionToken } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { EntityScreenResolver } from './entity-screens.resolver';
import { EntityDefinition } from '../base-entity-definition/entity-definition';
import { TEST_ENTITY_DEFINITIONS } from '../base-entity-definition/test-entity-definition';

const ENTITY_DEFINITIONS_URL = 'http://localhost:8080/organizations/acme/entity-definitions?page=0&size=200';

/** A compile-time facade, in the shape `BASE_ENTITY_FACADE_REGISTRY` points at. */
function facadeOf(descriptor: BaseEntityDescriptor) {
  return { descriptor, store: { entities: () => [] } };
}

function compiledDescriptor(entityName: string, embeddedChildName?: string): BaseEntityDescriptor {
  const attrDescriptors: BaseEntityAttrDescriptor[] = [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true)];
  if (embeddedChildName) {
    const embedded = new BaseEntityAttrDescriptor('parts', FormControlType.EMBEDDED_COMPONENTS, 'Parts');
    embedded.linkedEntityType = embeddedChildName;
    attrDescriptors.push(embedded);
  }
  return new BaseEntityDescriptor({ entityName, entityTitle: entityName, attrDescriptors });
}

describe('EntityScreenResolver', () => {
  let controller: HttpTestingController;

  function setup(facadeProviders: Array<Record<string, unknown>> = [], facadeRegistry: Record<string, unknown> = {}) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' } } },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: facadeRegistry },
        ...(facadeProviders as never[]),
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    return TestBed.inject(EntityScreenResolver);
  }

  /** Definitions are flushed while `resolve` awaits them; a compile-time hit never asks for them. */
  async function resolve(resolver: EntityScreenResolver, entityName: string, definitions: EntityDefinition[] = TEST_ENTITY_DEFINITIONS) {
    const resolving = resolver.resolve(entityName);
    controller.match(ENTITY_DEFINITIONS_URL).forEach((request) => request.flush({ content: definitions }));
    return resolving;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  describe('for an entity that exists only as metadata', () => {
    it('synthesizes the descriptor and the branch of its embedded child', async () => {
      const screens = await resolve(setup(), 'Order');

      expect(screens?.descriptor.entityName).toBe('Order');
      expect(screens?.embeddedChildren.map((child) => child.entityName)).toEqual(['Order Line']);
    });

    it('gives the embedded branch the facade token the child was built with, so its routes get its store', async () => {
      const screens = await resolve(setup(), 'Order');
      const facadeToken = screens?.embeddedChildren[0].facade;
      expect(facadeToken).toBeDefined();

      const facade = facadeToken ? TestBed.inject(facadeToken) : undefined;
      expect(facade?.entityName).toBe('Order Line');
    });

    it('answers undefined for a name nothing knows, which is what leaves the route a leaf', async () => {
      expect(await resolve(setup(), 'Nowhere')).toBeUndefined();
      expect(await setup().resolve(undefined)).toBeUndefined();
    });
  });

  describe('for a compiled-in entity', () => {
    it('resolves through the registered facade without fetching any definitions', async () => {
      const descriptor = compiledDescriptor('Test Entity');
      const facadeToken = new InjectionToken<unknown>('TEST_ENTITY_FACADE');
      const resolver = setup([{ provide: facadeToken, useValue: facadeOf(descriptor) }], { 'Test Entity': facadeToken });

      expect((await resolver.resolve('Test Entity'))?.descriptor).toBe(descriptor);
      controller.verify();
    });

    it('derives the embedded branches from the descriptor, so a compiled aggregate mounts the same way', async () => {
      const childToken = new InjectionToken<unknown>('EMBEDDED_FACADE');
      const parentToken = new InjectionToken<unknown>('PARENT_FACADE');
      const resolver = setup(
        [
          { provide: parentToken, useValue: facadeOf(compiledDescriptor('Parent', 'Child')) },
          { provide: childToken, useValue: facadeOf(compiledDescriptor('Child')) },
        ],
        { Parent: parentToken, Child: childToken },
      );

      const screens = await resolver.resolve('Parent');

      expect(screens?.embeddedChildren).toHaveLength(1);
      expect(screens?.embeddedChildren[0].entityName).toBe('Child');
      expect(screens?.embeddedChildren[0].facade).toBe(childToken);
      expect(screens?.embeddedChildren[0].children?.()).toEqual([]);
    });

    /**
     * A registration is the host application's explicit decision — it may carry extra tabs or a hand-tuned
     * layout a synthesized descriptor cannot know about — so a definition of the same name does not win.
     */
    it('wins over a definition of the same name', async () => {
      const descriptor = compiledDescriptor('Order');
      const facadeToken = new InjectionToken<unknown>('ORDER_FACADE');
      const resolver = setup([{ provide: facadeToken, useValue: facadeOf(descriptor) }], { Order: facadeToken });

      expect((await resolver.resolve('Order'))?.descriptor).toBe(descriptor);
      controller.verify();
    });
  });

  /**
   * The rows are part of the owner's payload and are still listed on its form; they just cannot be opened.
   * Refusing to mount the owner's screens over it would be the worse reading of an incomplete aggregate.
   */
  it('skips an embedded child nothing can resolve, keeping the owner mountable', async () => {
    const orphanOwner: EntityDefinition = {
      code: 'owner',
      name: 'Owner',
      attributes: [
        { code: 'name', formControlType: 'TEXT', isLinkToDetails: true },
        { code: 'rows', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'deleted-child' },
      ],
    };

    const screens = await resolve(setup(), 'Owner', [orphanOwner]);

    expect(screens?.descriptor.entityName).toBe('Owner');
    expect(screens?.embeddedChildren).toEqual([]);
  });

  it('expands a self-nesting child one level per navigation, rather than eagerly or not at all', async () => {
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

    const screens = await resolve(setup(), 'Nav Item', [selfNesting]);
    const child = screens?.embeddedChildren[0];

    expect(child?.entityName).toBe('Nav Item');
    // Each call of the thunk yields the next level, which is what makes the depth unbounded and finite.
    expect(child?.children?.().map((grandChild) => grandChild.entityName)).toEqual(['Nav Item']);
    expect(child?.children?.()[0].children?.().map((grandChild) => grandChild.entityName)).toEqual(['Nav Item']);
  });
});
