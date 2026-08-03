import { InjectionToken, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityDescriptorRegistry } from './base-entity-descriptor.registry';
import { BASE_ENTITY_FACADE_REGISTRY } from './base-entity-facade-registry';

function makeDescriptor(entityName: string, { componentParent, isEmbedded }: { componentParent?: string; isEmbedded?: boolean } = {}): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true)],
    entityName,
    entityTitle: entityName,
    componentParent,
    isEmbedded,
  });
}

function setupRegistry(providers: Provider[]): BaseEntityDescriptorRegistry {
  TestBed.configureTestingModule({ providers });
  return TestBed.inject(BaseEntityDescriptorRegistry);
}

describe('BaseEntityDescriptorRegistry', () => {
  describe('getDescriptor()', () => {
    it('resolves an entity that has a facade through that facade', () => {
      const descriptor = makeDescriptor('Test Entity');
      const store = { entities: vi.fn(() => []) };
      const facadeToken = new InjectionToken<unknown>('TEST_ENTITY_FACADE');
      const registry = setupRegistry([
        { provide: facadeToken, useValue: { descriptor, store } },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { 'Test Entity': facadeToken } },
      ]);

      expect(registry.getDescriptor('Test Entity')).toBe(descriptor);
      expect(registry.getStore('Test Entity')).toBe(store);
    });

    /**
     * An embedded entity is registered like any other. Its store is what makes its own list and form work;
     * that the store writes the containing entity's document is invisible from here.
     */
    it('resolves an embedded entity, store included, through its facade', () => {
      const descriptor = makeDescriptor('Embedded Component', { componentParent: 'Test Entity', isEmbedded: true });
      const store = { entities: vi.fn(() => []) };
      const facadeToken = new InjectionToken<unknown>('EMBEDDED_COMPONENT_FACADE');
      const registry = setupRegistry([
        { provide: facadeToken, useValue: { descriptor, store } },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { 'Embedded Component': facadeToken } },
      ]);

      expect(registry.getDescriptor('Embedded Component')).toBe(descriptor);
      expect(registry.getStore('Embedded Component')).toBe(store);
    });

    it('returns undefined for an unknown or missing entity name', () => {
      const registry = setupRegistry([{ provide: BASE_ENTITY_FACADE_REGISTRY, useValue: {} }]);

      expect(registry.getDescriptor('Nowhere')).toBeUndefined();
      expect(registry.getDescriptor(undefined)).toBeUndefined();
      expect(registry.getStore(undefined)).toBeUndefined();
    });

    it('returns undefined when a registered facade token is not provided', () => {
      const facadeToken = new InjectionToken<unknown>('MISSING_FACADE');
      const registry = setupRegistry([{ provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { 'Test Entity': facadeToken } }]);

      expect(registry.getDescriptor('Test Entity')).toBeUndefined();
    });
  });
});
