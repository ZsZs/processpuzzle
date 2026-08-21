import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { BASE_ENTITY_FACADE_REGISTRY, type EntityDefinition, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { RuleContextOptions } from './rule-context-options.service';

describe('RuleContextOptions', () => {
  const definition = (code: string, name: string) => [code, { code, name } as EntityDefinition] as const;

  function setup(facades: Record<string, unknown>, definitions: ReadonlyArray<readonly [string, EntityDefinition]>, deferred?: { resolve: () => void }) {
    const loaded = new Map<string, EntityDefinition>(definitions);
    TestBed.configureTestingModule({
      providers: [
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: facades },
        {
          provide: EntityDefinitionRegistry,
          useValue: {
            load: () => (deferred ? new Promise<ReadonlyMap<string, EntityDefinition>>((resolve) => (deferred.resolve = () => resolve(loaded))) : Promise.resolve(loaded)),
          },
        },
      ],
    });
    return TestBed.inject(RuleContextOptions);
  }

  it('offers the compiled-in entities', async () => {
    const options = setup({ Order: {}, Document: {} }, []);
    await Promise.resolve();

    expect(options.options()).toEqual([
      { key: 'Document', value: 'Document' },
      { key: 'Order', value: 'Order' },
    ]);
  });

  it('offers the metadata-defined entities by name, not by code', async () => {
    // The gap this service closes: three seeded rules name `Order` as their context while the entity exists
    // only as a definition, so a dropdown built from the facade registry alone could not select it.
    const options = setup({}, [definition('order', 'Order'), definition('order-line', 'Order Line')]);
    await Promise.resolve();

    expect(options.options().map((option) => option.value)).toEqual(['Order', 'Order Line']);
  });

  it('lists an entity once when a definition and a facade share its name', async () => {
    const options = setup({ Order: {} }, [definition('order', 'Order'), definition('special-order', 'Special Order')]);
    await Promise.resolve();

    expect(options.options().map((option) => option.value)).toEqual(['Order', 'Special Order']);
  });

  it('answers with the compiled entities before the definitions arrive', () => {
    // `getSelectables()` is synchronous and called on every change detection, so the dropdown has to render
    // something on the first pass rather than wait for the fetch.
    const deferred = { resolve: () => undefined };
    const options = setup({ Document: {} }, [definition('order', 'Order')], deferred);

    expect(options.options().map((option) => option.value)).toEqual(['Document']);
  });

  it('gains the defined entities once the fetch resolves', async () => {
    const deferred = { resolve: () => undefined };
    const options = setup({ Document: {} }, [definition('order', 'Order')], deferred);

    deferred.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(options.options().map((option) => option.value)).toEqual(['Document', 'Order']);
  });
});
