import type { Page } from '@playwright/test';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it, vi } from 'vitest';
import type { RouteResolver } from '../routing/route.resolver';
import { type EntityFixture, EntityCrudFixtureManager } from './entity-crud-fixture-manager';

describe('EntityCrudFixtureManager.afterRemoval', () => {
  const page = {} as Page;
  const manager = (hook?: (fixture: { entityName: string; id: string }, page: Page) => Promise<void>) =>
    new EntityCrudFixtureManager({} as RouteResolver, new Map<string, BaseEntityDescriptor>(), 'suffix', undefined, [], hook);
  const fixture = (id: string): EntityFixture => ({ entityName: 'Order', descriptor: {} as BaseEntityDescriptor, deleted: true, id, data: {}, displayValue: 'x' });

  it('tells the application which fixture is gone, by the id the application gave it', async () => {
    const hook = vi.fn(async () => undefined);

    await manager(hook).afterRemoval(page, fixture('o-1'));

    expect(hook).toHaveBeenCalledWith({ entityName: 'Order', id: 'o-1' }, page);
  });

  it('has nothing to point at for a fixture that never learned its id', async () => {
    const hook = vi.fn(async () => undefined);

    await manager(hook).afterRemoval(page, fixture(''));

    expect(hook).not.toHaveBeenCalled();
  });

  it('is a no-op for an application that gave no hook', async () => {
    await expect(manager().afterRemoval(page, fixture('o-1'))).resolves.toBeUndefined();
  });

  it('lets a failing hook fail the cleanup instead of leaking silently', async () => {
    const hook = vi.fn(async () => {
      throw new Error('instances unreachable');
    });

    await expect(manager(hook).afterRemoval(page, fixture('o-1'))).rejects.toThrow('instances unreachable');
  });
});
